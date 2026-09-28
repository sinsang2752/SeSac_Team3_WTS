package com.team.wts.trading.execution.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.trading.execution.domain.Execution;
import com.team.wts.trading.order.domain.OrderSide;

/** {@code execution.completed} payload. (CLAUDE.md §16) */
public record ExecutionCompleted(
        Long executionId,
        Long orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal price,
        long quantity,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal realizedProfit,
        Instant executedAt) {

    public static ExecutionCompleted from(Execution execution) {
        return new ExecutionCompleted(execution.id(), execution.orderId(), execution.accountId(),
                execution.symbol(), execution.side(), execution.price(), execution.quantity(),
                execution.amount(), execution.fee(), execution.realizedProfit(),
                execution.executedAt());
    }
}
