package com.team.wts.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 모든 Kafka 이벤트가 공유하는 봉투. (CLAUDE.md §16)
 *
 * <pre>
 * {
 *   "eventId": "uuid",
 *   "eventType": "market.price.updated",
 *   "occurredAt": "ISO-8601",
 *   "traceId": "uuid",
 *   "aggregateId": "005930",
 *   "version": 1,
 *   "payload": { ... }
 * }
 * </pre>
 *
 * <p>{@code eventId}는 Consumer가 중복 처리를 걸러내는 기준이다 (§17).
 *
 * <p>Jackson은 record의 정규 생성자로 역직렬화한다.
 * 빌드에 {@code -parameters} 플래그가 켜져 있어야 파라미터 이름이 유지된다
 * (루트 build.gradle 참고).
 *
 * @param <T> payload 타입
 */
public record DomainEvent<T>(
        String eventId,
        String eventType,
        Instant occurredAt,
        String traceId,
        String aggregateId,
        int version,
        T payload) {

    /** 현재 스키마 버전. payload 구조가 바뀌면 올린다. */
    public static final int CURRENT_VERSION = 1;

    public static <T> DomainEvent<T> of(String eventType, String aggregateId, String traceId, T payload) {
        return new DomainEvent<>(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                traceId,
                aggregateId,
                CURRENT_VERSION,
                payload);
    }
}
