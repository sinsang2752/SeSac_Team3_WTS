package com.team.wts.trading.account.event;

import java.math.BigDecimal;

import com.team.wts.trading.ledger.domain.LedgerEntryType;

/** {@code account.balance.changed} payload. (CLAUDE.md §16) */
public record AccountBalanceChanged(
        Long accountId,
        String userId,
        LedgerEntryType reason,
        BigDecimal amount,
        BigDecimal beforeBalance,
        BigDecimal afterBalance,
        BigDecimal reservedCash,
        BigDecimal availableCash) {
}
