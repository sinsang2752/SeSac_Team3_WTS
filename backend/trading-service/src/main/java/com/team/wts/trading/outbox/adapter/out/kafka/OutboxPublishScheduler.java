package com.team.wts.trading.outbox.adapter.out.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 발행을 주기적으로 돌린다. (CLAUDE.md §15)
 *
 * <p>{@link OutboxKafkaPublisher}와 <b>다른 빈</b>이어야 한다.
 * 같은 빈 안에서 스케줄 메서드가 {@code publishBatch()}를 부르면 Spring 프록시를 거치지 않아
 * {@code @Transactional}이 적용되지 않는다. 그러면 비관적 락 조회가 트랜잭션 없이 실행돼
 * 매 폴링마다 실패한다.
 */
@Component
public class OutboxPublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishScheduler.class);

    private final OutboxKafkaPublisher publisher;

    public OutboxPublishScheduler(OutboxKafkaPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${trading.outbox.poll-interval-ms:500}")
    public void publishPending() {
        try {
            publisher.publishBatch();
        } catch (Exception e) {
            // 스케줄러 스레드로 예외가 새면 이후 실행이 멈춘다.
            log.error("Outbox 발행 배치 실패", e);
        }
    }
}
