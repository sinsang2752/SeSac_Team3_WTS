package com.team.wts.trading.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox Publisher 설정. (CLAUDE.md §15)
 *
 * @param batchSize     한 번 폴링에 처리할 최대 건수
 * @param maxRetryCount 이만큼 실패하면 FAILED로 두고 더 시도하지 않는다
 * @param sendTimeout   Kafka 전송 응답을 기다리는 시간
 */
@ConfigurationProperties(prefix = "trading.outbox")
public record OutboxProperties(int batchSize, int maxRetryCount, Duration sendTimeout) {

    public OutboxProperties {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("trading.outbox.batch-size 는 1 이상이어야 한다: " + batchSize);
        }
        if (maxRetryCount <= 0) {
            throw new IllegalArgumentException(
                    "trading.outbox.max-retry-count 는 1 이상이어야 한다: " + maxRetryCount);
        }
        if (sendTimeout == null || sendTimeout.isNegative() || sendTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "trading.outbox.send-timeout 은 0보다 커야 한다: " + sendTimeout);
        }
    }
}
