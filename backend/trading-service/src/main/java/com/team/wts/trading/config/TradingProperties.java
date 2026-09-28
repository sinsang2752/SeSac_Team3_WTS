package com.team.wts.trading.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 거래 설정. (CLAUDE.md §9.1, §44)
 *
 * @param initialCash 신규 계좌 초기 가상자금. 기본 1억원이며 설정으로 바꿀 수 있어야 한다.
 * @param fee         수수료·세금 요율. MVP는 둘 다 0이다.
 */
@ConfigurationProperties(prefix = "trading")
public record TradingProperties(BigDecimal initialCash, Fee fee) {

    public TradingProperties {
        if (initialCash == null || initialCash.signum() < 0) {
            throw new IllegalArgumentException("trading.initial-cash 는 0 이상이어야 한다: " + initialCash);
        }
        if (fee == null) {
            fee = new Fee(BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    /**
     * @param rate    매수·매도 양쪽에 붙는 수수료율
     * @param taxRate 매도에만 붙는 세율
     */
    public record Fee(BigDecimal rate, BigDecimal taxRate) {

        public Fee {
            rate = requireNonNegative(rate, "trading.fee.rate");
            taxRate = requireNonNegative(taxRate, "trading.fee.tax-rate");
        }

        private static BigDecimal requireNonNegative(BigDecimal value, String name) {
            if (value == null) {
                return BigDecimal.ZERO;
            }
            if (value.signum() < 0) {
                throw new IllegalArgumentException(name + " 은 0 이상이어야 한다: " + value);
            }
            return value;
        }
    }
}
