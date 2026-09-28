package com.team.wts.trading.outbox.domain;

/** Outbox 발행 상태. */
public enum OutboxStatus {
    /** 아직 Kafka로 나가지 않았다. */
    PENDING,
    PUBLISHED,
    /** 재시도 한도를 넘겼다. 사람이 봐야 한다. */
    FAILED
}
