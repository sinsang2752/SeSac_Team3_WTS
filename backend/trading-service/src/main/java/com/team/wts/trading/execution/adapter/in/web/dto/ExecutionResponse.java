package com.team.wts.trading.execution.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.wts.trading.execution.domain.Execution;
import com.team.wts.trading.order.domain.OrderSide;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 체결 내역 응답. (CLAUDE.md §24)
 *
 * @param realizedProfit 매도 체결에만 있다. 체결 시점 평균단가 기준이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionResponse(
        @Schema(description = "체결 ID", example = "12")
        Long executionId,
        @Schema(description = "주문 ID", example = "8")
        Long orderId,
        @Schema(description = "종목코드", example = "005930")
        String symbol,
        @Schema(description = "BUY 매수 / SELL 매도", example = "SELL")
        OrderSide side,
        @Schema(description = "체결가 (원). 지정가 주문도 지정가가 아니라 체결 시점 현재가다", example = "91000")
        BigDecimal price,
        @Schema(description = "체결 수량 (주)", example = "4")
        long quantity,
        @Schema(description = "체결 대금 (원) = price × quantity. 수수료 미포함", example = "364000")
        BigDecimal amount,
        @Schema(description = "수수료 + 세금 (원). MVP는 0", example = "0")
        BigDecimal fee,
        @Schema(description = "실현손익 (원) = (체결가 - 체결 시점 평균단가) × 수량. 매도 체결에만 있다", example = "44000")
        BigDecimal realizedProfit,
        @Schema(description = "체결 시각 (UTC)", example = "2026-09-22T08:12:44.101Z")
        Instant executedAt) {

    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(execution.id(), execution.orderId(), execution.symbol(),
                execution.side(), execution.price(), execution.quantity(), execution.amount(),
                execution.fee(), execution.realizedProfit(), execution.executedAt());
    }
}
