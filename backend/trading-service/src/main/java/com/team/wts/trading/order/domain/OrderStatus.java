package com.team.wts.trading.order.domain;

/**
 * 주문 상태. (CLAUDE.md §9.6)
 *
 * <pre>
 *   RECEIVED ──validate──> VALIDATED ──accept──> ACCEPTED ──fill───> FILLED
 *      │                                             │
 *      └──reject──> REJECTED                         └──cancel─> CANCELLED
 * </pre>
 *
 * <p>{@code RECEIVED}와 {@code VALIDATED}는 주문 접수 트랜잭션 안에서만 스쳐 가는 상태다.
 * 트랜잭션이 끝난 주문은 항상 ACCEPTED / FILLED / CANCELLED / REJECTED 중 하나다.
 *
 * <p>{@code PARTIALLY_FILLED}는 MVP 선택 기능이라 넣지 않는다 (§9.6). 부분체결이 없으므로
 * 체결은 전량 체결뿐이다.
 */
public enum OrderStatus {

    RECEIVED,
    VALIDATED,
    ACCEPTED,
    FILLED,
    CANCELLED,
    REJECTED;

    /** 아직 체결을 기다리는 상태인가. 미체결 지정가 주문이 여기 해당한다. */
    public boolean isOpen() {
        return this == ACCEPTED;
    }

    /** 더 이상 바뀌지 않는 상태인가. */
    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED;
    }
}
