package com.team.wts.trading.order.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderType;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class PlaceOrderRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("지정가 주문에 limitPrice가 없으면 요청 검증에서 거절한다 (500이 아니라 400)")
    void limitOrderWithoutPriceIsInvalid() {
        var request = new PlaceOrderRequest("005930", OrderSide.BUY, OrderType.LIMIT, 1, null);

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("limitPriceProvided");
    }

    @Test
    @DisplayName("시장가 주문은 limitPrice가 없어도 된다")
    void marketOrderWithoutPriceIsValid() {
        var request = new PlaceOrderRequest("005930", OrderSide.BUY, OrderType.MARKET, 1, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("지정가 주문에 limitPrice가 있으면 통과한다")
    void limitOrderWithPriceIsValid() {
        var request = new PlaceOrderRequest("005930", OrderSide.BUY, OrderType.LIMIT, 1, new BigDecimal("80000"));

        assertThat(validator.validate(request)).isEmpty();
    }
}
