package com.team.wts.trading.position.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.trading.position.domain.Position;

/**
 * 보유 포지션 응답. (CLAUDE.md §24)
 *
 * <p>평가금액과 평가손익은 여기 없다. 현재가가 필요한 값이라 포트폴리오 화면에서
 * 실시간 시세와 함께 계산한다 (Phase 5).
 *
 * @param availableQuantity 매도 가능 수량 = {@code quantity - reservedQuantity} (§9.3)
 */
public record PositionResponse(
        String symbol,
        long quantity,
        long reservedQuantity,
        long availableQuantity,
        BigDecimal averagePrice,
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
