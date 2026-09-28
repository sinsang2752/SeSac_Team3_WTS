package com.team.wts.common.event;

/**
 * Kafka 토픽 이름. (CLAUDE.md §16)
 *
 * <p>발행하는 쪽과 구독하는 쪽이 다른 서비스이므로 문자열을 각자 적지 않고 여기서 공유한다.
 * 아직 발행자가 없는 토픽은 해당 Phase에서 추가한다 (§52).
 *
 * <p>토픽 이름과 {@code DomainEvent.eventType}은 같은 값이다.
 * Outbox가 이벤트 타입을 그대로 토픽으로 쓴다.
 */
public final class KafkaTopics {

    /** market-service가 시세 갱신마다 발행한다. */
    public static final String MARKET_PRICE_UPDATED = "market.price.updated";

    // ── 거래 (Phase 4) ── trading-service가 Outbox를 거쳐 발행한다 (§15).

    /** 주문이 저장됐다. 거절된 주문도 포함한다. */
    public static final String ORDER_CREATED = "order.created";
    /** 주문이 성립해 예수금 또는 수량을 묶었다. */
    public static final String ORDER_ACCEPTED = "order.accepted";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String EXECUTION_COMPLETED = "execution.completed";
    public static final String ACCOUNT_BALANCE_CHANGED = "account.balance.changed";
    public static final String POSITION_CHANGED = "position.changed";
    public static final String LEDGER_CREATED = "ledger.created";

    private KafkaTopics() {
    }
}
