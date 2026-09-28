package com.team.wts.trading.order.application.command;

import java.math.BigDecimal;

import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderType;

/**
 * 주문 접수 명령. (CLAUDE.md §45 – Command/Query 분리)
 *
 * @param idempotencyKey 클라이언트가 만든 중복 방지 키 (§14)
 */
public record PlaceOrderCommand(
        String userId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        long quantity,
        BigDecimal limitPrice,
        String idempotencyKey) {
}
