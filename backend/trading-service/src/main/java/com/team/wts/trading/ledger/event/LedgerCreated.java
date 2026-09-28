package com.team.wts.trading.ledger.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.trading.ledger.domain.LedgerEntry;
import com.team.wts.trading.ledger.domain.LedgerEntryType;

/** {@code ledger.created} payload. (CLAUDE.md §16) */
public record LedgerCreated(
        Long ledgerEntryId,
        Long accountId,
        Long orderId,
        Long executionId,
        LedgerEntryType type,
        BigDecimal amount,
        BigDecimal beforeBalance,
        BigDecimal afterBalance,
        Instant createdAt) {

    public static LedgerCreated from(LedgerEntry entry) {
        return new LedgerCreated(entry.id(), entry.accountId(), entry.orderId(),
                entry.executionId(), entry.type(), entry.amount(), entry.beforeBalance(),
                entry.afterBalance(), entry.createdAt());
    }
}
