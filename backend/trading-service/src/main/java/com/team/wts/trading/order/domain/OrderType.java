package com.team.wts.trading.order.domain;

/** 주문 종류. (CLAUDE.md §9.7) */
public enum OrderType {
    /** 최신 현재가로 즉시 체결한다 (§11). */
    MARKET,
    /** 가격 조건을 만족할 때만 체결한다 (§12). */
    LIMIT
}
