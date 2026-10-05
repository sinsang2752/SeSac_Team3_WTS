package com.team.wts.trading.position.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.trading.position.domain.Position;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 보유 포지션 응답. (CLAUDE.md §24)
 *
 * <p>평가금액과 평가손익은 여기 없다. 현재가가 필요한 값이라 포트폴리오 화면에서
 * 실시간 시세와 함께 계산한다 (Phase 5).
 *
 * @param availableQuantity 매도 가능 수량 = {@code quantity - reservedQuantity} (§9.3)
 */
public record PositionResponse(
        @Schema(description = "종목코드", example = "005930")
        String symbol,
        @Schema(description = "보유 수량 (주)", example = "10")
        long quantity,
        @Schema(description = "미체결 매도 주문이 묶어둔 수량 (주)", example = "4")
        long reservedQuantity,
        @Schema(description = "매도 가능 수량 (주) = quantity - reservedQuantity", example = "6")
        long availableQuantity,
        @Schema(description = "평균 매입단가 (원). 이동평균이며 매도는 이 값을 바꾸지 않는다", example = "80300")
        BigDecimal averagePrice,
        @Schema(description = "마지막 변경 시각 (UTC)", example = "2026-09-22T08:12:44.101Z")
        Instant updatedAt) {

    public static PositionResponse from(Position position) {
        return new PositionResponse(
                position.symbol(),
                position.quantity(),
                position.reservedQuantity(),
                position.availableQuantity(),
                position.averagePrice(),
                position.updatedAt());
    }
}
