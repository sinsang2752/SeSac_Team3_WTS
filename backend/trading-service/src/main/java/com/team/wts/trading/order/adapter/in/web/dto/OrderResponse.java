package com.team.wts.trading.order.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderStatus;
import com.team.wts.trading.order.domain.OrderType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주문 조회 응답. (CLAUDE.md §24)
 *
 * @param rejectReason REJECTED 주문에만 있다. 나머지는 응답에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        @Schema(description = "주문 ID", example = "2")
        Long orderId,
        @Schema(description = "종목코드", example = "005930")
        String symbol,
        @Schema(description = "BUY 매수 / SELL 매도", example = "BUY")
        OrderSide side,
        @Schema(description = "MARKET 시장가 / LIMIT 지정가", example = "LIMIT")
        OrderType orderType,
        @Schema(description = "주문 수량 (주)", example = "10")
        long quantity,
        @Schema(description = "지정가 (원). 시장가 주문이면 필드가 없다", example = "80000")
        BigDecimal limitPrice,
        @Schema(description = "체결된 수량 (주)", example = "0")
        long filledQuantity,
        @Schema(description = "주문 상태. FILLED 전량 체결 / ACCEPTED 미체결(예수금·수량 묶임) / CANCELLED 취소 / REJECTED 거절", example = "ACCEPTED")
        OrderStatus status,
        @Schema(description = "거절 사유 에러 코드 (예: INSUFFICIENT_BALANCE). REJECTED 주문에만 있다")
        String rejectReason,
        @Schema(description = "주문 시각 (UTC)", example = "2026-09-22T08:12:44.101Z")
        Instant createdAt,
        @Schema(description = "마지막 상태 변경 시각 (UTC)", example = "2026-09-22T08:12:44.101Z")
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
