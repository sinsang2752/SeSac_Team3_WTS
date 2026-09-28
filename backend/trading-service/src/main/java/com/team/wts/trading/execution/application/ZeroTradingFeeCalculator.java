package com.team.wts.trading.execution.application;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.team.wts.trading.config.TradingProperties;
import com.team.wts.trading.execution.domain.TradingFeeCalculator;
import com.team.wts.trading.order.domain.OrderSide;

/**
 * 설정된 요율로 수수료를 계산한다. 기본값은 0이다. (CLAUDE.md §44)
 *
 * <p>매도에만 붙는 세금과 양쪽에 붙는 수수료를 나눠 둔 것은 실제 한국 주식 거래 구조를
 * 따른 것이다. 둘 다 기본 0이라 MVP에서는 수수료 원장 기록이 생기지 않는다.
 */
@Component
public class ZeroTradingFeeCalculator implements TradingFeeCalculator {

    private final TradingProperties properties;

    public ZeroTradingFeeCalculator(TradingProperties properties) {
        this.properties = properties;
    }

    @Override
    public BigDecimal calculate(OrderSide side, BigDecimal amount) {
        BigDecimal rate = properties.fee().rate();
        if (side == OrderSide.SELL) {
            rate = rate.add(properties.fee().taxRate());
        }
        if (rate.signum() == 0) {
            return BigDecimal.ZERO;
        }
        // 원 단위 미만은 버린다. 사용자에게 유리한 방향이다.
        return amount.multiply(rate).setScale(0, java.math.RoundingMode.DOWN);
    }
}
