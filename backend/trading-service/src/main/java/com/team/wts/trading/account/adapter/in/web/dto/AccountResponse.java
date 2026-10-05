package com.team.wts.trading.account.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.trading.account.domain.VirtualAccount;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 계좌 조회 응답. (CLAUDE.md §24 – GET /api/trading/account)
 *
 * @param availableCash 주문 가능 금액. {@code cashBalance - reservedCash} (§9.2)
 */
public record AccountResponse(
        @Schema(description = "가상 계좌 ID", example = "1")
        Long accountId,
        @Schema(description = "사용자 ID (UUID)", example = "59407a60-bc3b-4a55-a452-72d0bcd72142")
        String userId,
        @Schema(description = "예수금 (원). 미체결 주문의 예약금이 빠지지 않은 값이다", example = "99197000")
        BigDecimal cashBalance,
        @Schema(description = "미체결 매수 주문이 묶어둔 금액 (원)", example = "645000")
        BigDecimal reservedCash,
        @Schema(description = "주문 가능 금액 (원) = cashBalance - reservedCash", example = "98552000")
        BigDecimal availableCash,
        @Schema(description = "마지막 변경 시각 (UTC)", example = "2026-09-22T08:12:44.101Z")
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
