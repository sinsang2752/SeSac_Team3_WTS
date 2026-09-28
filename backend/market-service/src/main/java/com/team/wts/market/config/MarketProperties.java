package com.team.wts.market.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시세 관련 설정. (CLAUDE.md §19, §21, §31)
 *
 * @param provider mock | kis. 어댑터 선택 기준이다.
 * @param stocks   구독 대상 고정 종목 목록. MVP는 고정이고, Dynamic Subscription은 이후 단계다 (§21).
 * @param mock     Mock Market Mode 동작 파라미터
 * @param price    시세 유효성 판단 기준
 */
@ConfigurationProperties(prefix = "market")
public record MarketProperties(
        String provider,
        List<StockConfig> stocks,
        Mock mock,
        Price price) {

    public MarketProperties {
        stocks = stocks == null ? List.of() : List.copyOf(stocks);
        mock = mock == null ? new Mock(null, null, null, null) : mock;
        price = price == null ? new Price(null) : price;
    }

    /**
     * @param previousClose 전일 종가. 등락률 계산 기준이자 Mock 가격 흐름의 출발점이다.
     */
    public record StockConfig(
            String symbol,
            String name,
            String market,
            BigDecimal previousClose) {
    }

    /**
     * @param tickInterval  시세 생성 주기
     * @param minChangeRate 한 틱당 최소 변동률 (0.001 = 0.1%)
     * @param maxChangeRate 한 틱당 최대 변동률 (0.005 = 0.5%)
     * @param seed          난수 시드. 비우면 실행마다 다른 흐름. 테스트는 고정값을 넣어 재현한다 (§31).
     */
    public record Mock(
            Duration tickInterval,
            BigDecimal minChangeRate,
            BigDecimal maxChangeRate,
            Long seed) {

        public Mock {
            tickInterval = tickInterval == null ? Duration.ofSeconds(1) : tickInterval;
            minChangeRate = minChangeRate == null ? new BigDecimal("0.001") : minChangeRate;
            maxChangeRate = maxChangeRate == null ? new BigDecimal("0.005") : maxChangeRate;
        }
    }

    /**
     * @param maxAge 이 시간을 넘긴 시세는 stale로 본다. 시장가 주문 거절 기준이다 (§11).
     */
    public record Price(Duration maxAge) {

        public Price {
            maxAge = maxAge == null ? Duration.ofSeconds(5) : maxAge;
        }
    }
}
