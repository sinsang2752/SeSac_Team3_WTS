package com.team.wts.trading.order.adapter.in.web.dto;

import java.math.BigDecimal;

import com.team.wts.trading.order.application.command.PlaceOrderCommand;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 주문 요청. (CLAUDE.md §24)
 *
 * <pre>
 * { "symbol": "005930", "side": "BUY", "orderType": "LIMIT", "quantity": 10, "limitPrice": 80000 }
 * </pre>
 */
public record PlaceOrderRequest(
        @NotNull
        @Pattern(regexp = "\\d{6}", message = "종목코드는 6자리 숫자입니다.")
        String symbol,

        @NotNull
        OrderSide side,

        @NotNull
        OrderType orderType,

        @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "주문 수량은 1,000,000 이하여야 합니다.")
        long quantity,

        // 금액은 BigDecimal로 받는다. 부동소수는 금지 (CLAUDE.md §42).
        @DecimalMin(value = "0", inclusive = false, message = "지정가는 0보다 커야 합니다.")
        @Digits(integer = 15, fraction = 4, message = "지정가 형식이 올바르지 않습니다.")
        BigDecimal limitPrice) {

    public PlaceOrderCommand toCommand(String userId, String idempotencyKey) {
        // 시장가에 limitPrice가 섞여 오면 무시한다. 무엇으로 체결할지는 orderType이 정한다.
        BigDecimal effectiveLimitPrice = orderType == OrderType.LIMIT ? limitPrice : null;
        return new PlaceOrderCommand(userId, symbol, side, orderType, quantity,
                effectiveLimitPrice, idempotencyKey);
    }
}
