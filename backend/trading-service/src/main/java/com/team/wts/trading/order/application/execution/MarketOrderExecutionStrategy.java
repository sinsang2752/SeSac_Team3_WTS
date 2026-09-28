package com.team.wts.trading.order.application.execution;

import org.springframework.stereotype.Component;

import com.team.wts.trading.market.domain.MarketPrice;
import com.team.wts.trading.order.domain.ExecutionResult;
import com.team.wts.trading.order.domain.ExecutionStrategy;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderType;

/**
 * 시장가 체결. (CLAUDE.md §11)
 *
 * <pre>현재 최신 가격 = 체결가격</pre>
 *
 * <p>시세가 stale이면 애초에 주문이 거절되므로 여기까지 오지 않는다.
 */
@Component
public class MarketOrderExecutionStrategy implements ExecutionStrategy {

    @Override
    public OrderType supports() {
        return OrderType.MARKET;
    }

    @Override
    public boolean canExecute(Order order, MarketPrice price) {
        return true;
    }

    @Override
    public ExecutionResult execute(Order order, MarketPrice price) {
        return new ExecutionResult(price.price(), order.remainingQuantity());
    }
}
