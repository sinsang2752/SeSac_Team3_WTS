package com.team.wts.trading.execution.domain;

import java.math.BigDecimal;

import com.team.wts.trading.order.domain.OrderSide;

/**
 * 거래 수수료·세금. (CLAUDE.md §44)
 *
 * <p>MVP는 0이지만 계산을 체결 로직에 박아 넣지 않는다.
 * 수수료가 생기면 이 구현체 하나만 바꾼다.
 */
public interface TradingFeeCalculator {

    /**
     * @param amount 체결 대금 (체결가 × 수량)
     * @return 0 이상의 수수료 합계
     */
    BigDecimal calculate(OrderSide side, BigDecimal amount);
}
