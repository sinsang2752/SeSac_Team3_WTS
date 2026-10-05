package com.team.wts.trading.outbox.adapter.out.kafka;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.trading.config.OutboxProperties;
import com.team.wts.trading.outbox.domain.OutboxEvent;
import com.team.wts.trading.outbox.domain.OutboxEventRepository;

/**
 * Outbox → Kafka Polling Publisher. (CLAUDE.md §15)
 *
 * <p>CDC/Debezium은 MVP 필수가 아니다. 폴링으로 충분하다.
 *
 * <p>트랜잭션을 Kafka 전송이 끝날 때까지 들고 있다. 잡은 행에 락이 걸린 채로 I/O를 하는 셈이라
 * 배치를 작게 유지한다. 대신 전송에 실패하면 트랜잭션이 통째로 되돌아가 행이 PENDING으로 남는다.
 * "보냈는데 PUBLISHED로 못 적는" 창이 없다.
 *
 * <p>같은 이유로 <b>At-Least-Once</b>다. 커밋 직전에 죽으면 같은 메시지가 다시 나간다.
 * 그래서 봉투의 eventId가 재시도에도 그대로여야 한다 (§17).
 *
 * <p>주기 실행은 {@code OutboxPublishScheduler}가 맡는다. 같은 빈에서 스케줄 메서드가
 * {@link #publishBatch}를 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다.
 */
@Component
public class OutboxKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxKafkaPublisher.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxKafkaPublisher(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka,
            OutboxProperties properties, Clock clock) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return 처리한 건수. */
    @Transactional
    public int publishBatch() {
        List<OutboxEvent> pending = outbox.claimPending(properties.batchSize());
        for (OutboxEvent event : pending) {
            publish(event);
        }
        return pending.size();
    }

    private void publish(OutboxEvent event) {
        try {
            // 토픽 = 이벤트 타입, 키 = 애그리거트 ID (파티션 내 순서 보장).
            kafka.send(event.eventType(), event.aggregateId(), event.payloadJson())
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            event.markPublished(clock.instant());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Outbox 발행이 중단됐다: outboxId=" + event.id(), e);
        } catch (Exception e) {
            event.markRetry(properties.maxRetryCount());
            log.error("Outbox 발행 실패: outboxId={} eventType={} retryCount={} status={}",
                    event.id(), event.eventType(), event.retryCount(), event.status(), e);
        }
    }
}
