package com.team.wts.trading.account.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.trading.account.domain.VirtualAccount;

/**
 * 계좌 조회 응답. (CLAUDE.md §24 – GET /api/trading/account)
 *
 * @param availableCash 주문 가능 금액. {@code cashBalance - reservedCash} (§9.2)
 */
public record AccountResponse(
        Long accountId,
        String userId,
        BigDecimal cashBalance,
        BigDecimal reservedCash,
        BigDecimal availableCash,
        Instant updatedAt) {

    public static AccountResponse from(VirtualAccount account) {
        return new AccountResponse(
                account.id(),
                account.userId(),
                account.cashBalance(),
                account.reservedCash(),
                account.availableCash(),
                account.updatedAt());
    }
}
