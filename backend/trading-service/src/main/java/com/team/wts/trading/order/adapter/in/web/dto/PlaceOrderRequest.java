package com.team.wts.trading.order.adapter.in.web.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.team.wts.trading.order.application.command.PlaceOrderCommand;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "종목코드. 6자리 숫자", example = "005930")
        String symbol,

        @NotNull
        @Schema(description = "BUY 매수 / SELL 매도", example = "BUY")
        OrderSide side,

        @NotNull
        @Schema(description = "MARKET 시장가 / LIMIT 지정가", example = "LIMIT")
        OrderType orderType,

        @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "주문 수량은 1,000,000 이하여야 합니다.")
        // primitive라 @NotNull 이 없다. 빠지면 0이 되어 @Min 에 걸린다. 명세에는 필수로 표시한다.
        @Schema(description = "주문 수량 (주). 1 ~ 1,000,000", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
        long quantity,

        // 금액은 BigDecimal로 받는다. 부동소수는 금지 (CLAUDE.md §42).
        @DecimalMin(value = "0", inclusive = false, message = "지정가는 0보다 커야 합니다.")
        @Digits(integer = 15, fraction = 4, message = "지정가 형식이 올바르지 않습니다.")
        @Schema(description = "지정가 (원). LIMIT 주문에 필수, 0보다 커야 한다. MARKET 주문이면 무시한다", example = "80000")
        BigDecimal limitPrice) {

    /**
     * 지정가 주문에는 지정가가 있어야 한다. 도메인({@code Order.receive})도 같은 규칙을 검사하지만
     * 그쪽은 내부 불변식 가드라 위반 시 500이 된다. 클라이언트 입력은 여기서 400으로 끊는다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "지정가(LIMIT) 주문에는 limitPrice가 필요합니다.")
    public boolean isLimitPriceProvided() {
        return orderType != OrderType.LIMIT || limitPrice != null;
    }

    public PlaceOrderCommand toCommand(String userId, String idempotencyKey) {
        // 시장가에 limitPrice가 섞여 오면 무시한다. 무엇으로 체결할지는 orderType이 정한다.
        BigDecimal effectiveLimitPrice = orderType == OrderType.LIMIT ? limitPrice : null;
        return new PlaceOrderCommand(userId, symbol, side, orderType, quantity,
                effectiveLimitPrice, idempotencyKey);
    }
}
