package com.team.wts.trading.order.application.execution;

import org.springframework.stereotype.Component;

import com.team.wts.trading.market.domain.MarketPrice;
import com.team.wts.trading.order.domain.ExecutionResult;
import com.team.wts.trading.order.domain.ExecutionStrategy;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderType;

/**
 * 지정가 체결. (CLAUDE.md §12)
 *
 * <pre>
 * BUY  : current_price &lt;= limit_price
 * SELL : current_price &gt;= limit_price
 * </pre>
 *
 * <p>체결가는 <b>현재가</b>다. 지정가보다 유리하게 체결되면 그 이득은 주문자의 것이다.
 * 예약해 둔 예수금과 실제 체결 대금의 차액은 주문 처리 과정에서 계좌로 돌아간다.
 */
@Component
public class LimitOrderExecutionStrategy implements ExecutionStrategy {

    @Override
    public OrderType supports() {
        return OrderType.LIMIT;
    }

    @Override
    public boolean canExecute(Order order, MarketPrice price) {
        int comparison = price.price().compareTo(order.limitPrice());
        return order.side() == OrderSide.BUY ? comparison <= 0 : comparison >= 0;
    }

    @Override
    public ExecutionResult execute(Order order, MarketPrice price) {
        if (!canExecute(order, price)) {
            throw new IllegalStateException(
                    "가격 조건을 만족하지 않는다: current=" + price.price() + " limit=" + order.limitPrice());
        }
        return new ExecutionResult(price.price(), order.remainingQuantity());
    }
}
