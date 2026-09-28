package com.team.wts.trading.order.domain;

import com.team.wts.trading.market.domain.MarketPrice;

/**
 * 주문 종류별 체결 규칙. (CLAUDE.md §10)
 *
 * <p>구현체는 순수 함수다. 시세가 신선한지(§11)는 호출자가 미리 판단한다.
 * 여기서 Clock을 들고 있으면 "가격 조건" 규칙과 "시세 신선도" 규칙이 한데 섞인다.
 */
public interface ExecutionStrategy {

    OrderType supports();

    boolean canExecute(Order order, MarketPrice price);

    ExecutionResult execute(Order order, MarketPrice price);
}
