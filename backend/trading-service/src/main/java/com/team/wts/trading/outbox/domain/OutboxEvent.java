package com.team.wts.trading.outbox.domain;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 발행 대기 중인 도메인 이벤트. (CLAUDE.md §15, §23 outbox_events)
 *
 * <p>거래 트랜잭션이 주문/계좌 변경과 <b>같은 커밋</b>으로 이 행을 남긴다.
 * Kafka 발행을 트랜잭션 안에서 하면 커밋은 성공했는데 발행이 실패하거나 그 반대가 생긴다.
 * 그 Dual Write 문제를 없애는 것이 이 테이블의 유일한 목적이다.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "aggregate_type", length = 30, nullable = false, updatable = false)
    private String aggregateType;

    /** Kafka 메시지 키. 같은 애그리거트의 이벤트 순서를 파티션 안에서 보장한다. */
    @Column(name = "aggregate_id", length = 64, nullable = false, updatable = false)
    private String aggregateId;

    /** 토픽 이름과 같다. */
    @Column(name = "event_type", length = 60, nullable = false, updatable = false)
    private String eventType;

    /** {@code DomainEvent} 봉투 전체. eventId가 들어 있어 재시도해도 같은 메시지다 (§17). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12, nullable = false)
    private OutboxStatus status;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** JPA 전용. */
    protected OutboxEvent() {
    }

    private OutboxEvent(String aggregateType, String aggregateId, String eventType, String payloadJson) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public static OutboxEvent pending(String aggregateType, String aggregateId, String eventType,
            String payloadJson) {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payloadJson, "payloadJson");
        return new OutboxEvent(aggregateType, aggregateId, eventType, payloadJson);
    }

    public void markPublished(Instant now) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
    }

    /**
     * 발행에 실패했다. 한도를 넘기면 더 시도하지 않는다.
     *
     * <p>FAILED로 두고 멈추는 이유는, 계속 재시도하면 뒤따르는 이벤트가 영원히 막히기 때문이다.
     * 남은 행은 사람이 보고 처리한다. DLQ는 MVP 후반이다 (CLAUDE.md §17).
     */
    public void markRetry(int maxRetryCount) {
        this.retryCount++;
        if (this.retryCount >= maxRetryCount) {
            this.status = OutboxStatus.FAILED;
        }
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long id() {
        return id;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public String payloadJson() {
        return payloadJson;
    }

    public OutboxStatus status() {
        return status;
    }

    public int retryCount() {
        return retryCount;
    }

    public Instant publishedAt() {
        return publishedAt;
    }
}
