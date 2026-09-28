package com.team.wts.trading.execution.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.wts.trading.execution.domain.Execution;
import com.team.wts.trading.order.domain.OrderSide;

/**
 * 체결 내역 응답. (CLAUDE.md §24)
 *
 * @param realizedProfit 매도 체결에만 있다. 체결 시점 평균단가 기준이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionResponse(
        Long executionId,
        Long orderId,
        String symbol,
        OrderSide side,
        BigDecimal price,
        long quantity,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal realizedProfit,
        Instant executedAt) {

    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(execution.id(), execution.orderId(), execution.symbol(),
                execution.side(), execution.price(), execution.quantity(), execution.amount(),
                execution.fee(), execution.realizedProfit(), execution.executedAt());
    }
}
