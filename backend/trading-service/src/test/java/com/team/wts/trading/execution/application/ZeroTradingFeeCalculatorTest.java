package com.team.wts.trading.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team.wts.trading.config.TradingProperties;
import com.team.wts.trading.order.domain.OrderSide;

/** 수수료 계산. (CLAUDE.md §44) */
class ZeroTradingFeeCalculatorTest {

    private static ZeroTradingFeeCalculator calculator(String rate, String taxRate) {
        return new ZeroTradingFeeCalculator(new TradingProperties(new BigDecimal("100000000"),
                new TradingProperties.Fee(new BigDecimal(rate), new BigDecimal(taxRate))));
    }

    @Test
    @DisplayName("MVP 기본 설정에서는 수수료가 0이다")
    void zeroByDefault() {
        ZeroTradingFeeCalculator calculator = calculator("0", "0");

        assertThat(calculator.calculate(OrderSide.BUY, new BigDecimal("800000")))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(calculator.calculate(OrderSide.SELL, new BigDecimal("800000")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("요율을 켜면 매도에는 세금이 더 붙는다")
    void sellAddsTax() {
        // 수수료 0.015%, 세금 0.18%
        ZeroTradingFeeCalculator calculator = calculator("0.00015", "0.0018");

        assertThat(calculator.calculate(OrderSide.BUY, new BigDecimal("1000000")))
                .isEqualByComparingTo("150");
        assertThat(calculator.calculate(OrderSide.SELL, new BigDecimal("1000000")))
                .isEqualByComparingTo("1950");
    }

    @Test
    @DisplayName("원 단위 미만은 버린다")
    void truncatesBelowWon() {
        ZeroTradingFeeCalculator calculator = calculator("0.00015", "0");

        // 79,999 × 0.00015 = 11.99985
        assertThat(calculator.calculate(OrderSide.BUY, new BigDecimal("79999")))
                .isEqualByComparingTo("11");
    }
}
