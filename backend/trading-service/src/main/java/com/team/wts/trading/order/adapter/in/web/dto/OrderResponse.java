package com.team.wts.trading.order.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderStatus;
import com.team.wts.trading.order.domain.OrderType;

/**
 * 주문 조회 응답. (CLAUDE.md §24)
 *
 * @param rejectReason REJECTED 주문에만 있다. 나머지는 응답에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        Long orderId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        long quantity,
        BigDecimal limitPrice,
        long filledQuantity,
        OrderStatus status,
        String rejectReason,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.symbol(),
                order.side(),
                order.orderType(),
                order.quantity(),
                order.limitPrice(),
                order.filledQuantity(),
                order.status(),
                order.rejectReason(),
                order.createdAt(),
                order.updatedAt());
    }
}
