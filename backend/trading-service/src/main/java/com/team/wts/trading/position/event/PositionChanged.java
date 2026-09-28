package com.team.wts.trading.position.event;

import java.math.BigDecimal;

import com.team.wts.trading.position.domain.Position;

/** {@code position.changed} payload. (CLAUDE.md §16) */
public record PositionChanged(
        Long accountId,
        String symbol,
        long quantity,
        long reservedQuantity,
        long availableQuantity,
        BigDecimal averagePrice) {

    public static PositionChanged from(Position position) {
        return new PositionChanged(position.accountId(), position.symbol(), position.quantity(),
                position.reservedQuantity(), position.availableQuantity(), position.averagePrice());
    }
}
