package com.team.wts.trading.order.adapter.in.kafka;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.wts.common.event.DomainEvent;
import com.team.wts.common.event.KafkaTopics;
import com.team.wts.common.web.TraceIdFilter;
import com.team.wts.trading.config.MarketPriceProperties;
import com.team.wts.trading.market.domain.MarketPrice;
import com.team.wts.trading.order.application.service.PendingOrderExecutionService;

/**
 * {@code market.price.updated}를 소비해 미체결 지정가 주문을 깨운다. (CLAUDE.md §12)
 *
 * <p>메시지를 문자열로 받아 직접 역직렬화한다. Kafka 타입 헤더에 의존하면 발행 측
 * 클래스 이름이 소비 측 계약이 되어버린다. market-service와 같은 방식이다.
 *
 * <p>체결가는 <b>이벤트에 실린 가격</b>이다. Valkey의 최신값이 아니다.
 * 가격 조건을 만족시킨 바로 그 가격으로 체결하는 것이 §12의 규칙에 맞고,
 * 테스트도 결정적으로 만들 수 있다.
 */
@Component
public class MarketPriceUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceUpdatedConsumer.class);

    private static final TypeReference<DomainEvent<PriceSnapshot>> EVENT_TYPE =
            new TypeReference<>() { };

    private final PendingOrderExecutionService pendingOrders;
    private final MarketPriceProperties priceProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MarketPriceUpdatedConsumer(PendingOrderExecutionService pendingOrders,
            MarketPriceProperties priceProperties, ObjectMapper objectMapper, Clock clock) {
        this.pendingOrders = pendingOrders;
        this.priceProperties = priceProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @KafkaListener(
            topics = KafkaTopics.MARKET_PRICE_UPDATED,
            groupId = "${trading.pending-order.consumer-group:trading-service-pending-order}")
    public void onPriceUpdated(String message, Acknowledgment acknowledgment) {
        try {
            handle(message);
        } finally {
            // 처리에 실패해도 오프셋을 넘긴다. 시세는 1초마다 다시 오므로
            // 같은 메시지를 붙잡고 재시도하는 것보다 다음 시세로 넘어가는 편이 낫다.
            acknowledgment.acknowledge();
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }

    private void handle(String message) {
        DomainEvent<PriceSnapshot> event;
        try {
            event = objectMapper.readValue(message, EVENT_TYPE);
        } catch (Exception e) {
            // 형식이 깨진 메시지는 재시도해도 같은 결과다. DLQ는 MVP 후반이다 (§17).
            log.error("시세 이벤트 파싱 실패, 건너뜀: {}", message, e);
            return;
        }

        // 주문을 깨운 시세 요청의 traceId를 이어받는다 (CLAUDE.md §40).
        if (event.traceId() != null) {
            MDC.put(TraceIdFilter.MDC_KEY, event.traceId());
        }

        PriceSnapshot payload = event.payload();
        MarketPrice price =
                new MarketPrice(payload.symbol(), payload.price(), payload.timestamp());

        // 소비가 밀렸다면 낡은 가격으로 체결하지 않는다 (CLAUDE.md §11).
        if (price.isStale(priceProperties.maxAge(), clock.instant())) {
            return;
        }

        List<Long> candidates = pendingOrders.findCandidateOrderIds(price.symbol());
        for (Long orderId : candidates) {
            try {
                pendingOrders.execute(orderId, price);
            } catch (Exception e) {
                // 한 주문의 실패가 나머지를 막지 않는다. 다음 시세에 다시 시도된다.
                log.error("미체결 주문 체결 실패: orderId={} symbol={}", orderId, price.symbol(), e);
            }
        }
    }

    /**
     * {@code market.price.updated} payload 중 체결 판정에 쓰는 부분.
     *
     * <p>market-service의 발행 클래스를 공유하지 않는다. 필요한 필드만 읽고 나머지는 무시한다.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceSnapshot(
            String symbol,
            java.math.BigDecimal price,
            java.time.Instant timestamp) {
    }
}
