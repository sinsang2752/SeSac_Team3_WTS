package com.team.wts.common.market;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockSymbolTest {

    @Test
    @DisplayName("숫자 6자리와 영문이 섞인 6자리를 모두 받는다")
    void acceptsNumericAndAlphanumericCodes() {
        assertThat(StockSymbol.isValid("005930")).isTrue();
        assertThat(StockSymbol.isValid("0001A0")).isTrue();
        assertThat(StockSymbol.isValid("00088K")).isTrue();
    }

    @Test
    @DisplayName("길이가 다르거나 소문자·기호가 있으면 거절한다")
    void rejectsOtherShapes() {
        assertThat(StockSymbol.isValid(null)).isFalse();
        assertThat(StockSymbol.isValid("00593")).isFalse();
        assertThat(StockSymbol.isValid("0059300")).isFalse();
        assertThat(StockSymbol.isValid("0001a0")).isFalse();
        assertThat(StockSymbol.isValid("00-930")).isFalse();
    }
}
