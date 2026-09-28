package com.team.wts.trading.order.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.team.wts.trading.market.domain.MarketPrice;
import com.team.wts.trading.order.domain.ExecutionResult;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderType;

/** 체결 규칙. (CLAUDE.md §10, §11, §12) */
class ExecutionStrategyTest {

    private static MarketPrice priceOf(String price) {
        return new MarketPrice("005930", new BigDecimal(price), Instant.parse("2026-09-21T00:00:00Z"));
    }

    private static Order accepted(OrderSide side, OrderType type, String limitPrice, long quantity) {
        Order order = Order.receive(1L, "005930", side, type, quantity,
                limitPrice == null ? null : new BigDecimal(limitPrice), "key-1");
        order.validate();
        order.accept();
        return order;
    }

    @Nested
    @DisplayName("시장가 (CLAUDE.md §11)")
    class Market {

        private final MarketOrderExecutionStrategy strategy = new MarketOrderExecutionStrategy();

        @Test
        void 담당_주문_종류는_MARKET이다() {
            assertThat(strategy.supports()).isEqualTo(OrderType.MARKET);
        }

        @Test
        void 현재가로_즉시_전량_체결한다() {
            Order order = accepted(OrderSide.BUY, OrderType.MARKET, null, 10);

            assertThat(strategy.canExecute(order, priceOf("79800"))).isTrue();

            ExecutionResult result = strategy.execute(order, priceOf("79800"));
            assertThat(result.price()).isEqualByComparingTo("79800");
            assertThat(result.quantity()).isEqualTo(10);
            assertThat(result.amount()).isEqualByComparingTo("798000");
        }
    }

    @Nested
    @DisplayName("지정가 (CLAUDE.md §12)")
    class Limit {

        private final LimitOrderExecutionStrategy strategy = new LimitOrderExecutionStrategy();

        @Test
        void 담당_주문_종류는_LIMIT이다() {
            assertThat(strategy.supports()).isEqualTo(OrderType.LIMIT);
        }

        @Test
        void 매수는_현재가가_지정가_이하일_때_체결한다() {
            Order buy = accepted(OrderSide.BUY, OrderType.LIMIT, "80000", 10);

            assertThat(strategy.canExecute(buy, priceOf("79900"))).isTrue();
            assertThat(strategy.canExecute(buy, priceOf("80000"))).isTrue();
            assertThat(strategy.canExecute(buy, priceOf("80100"))).isFalse();
        }

        @Test
        void 매도는_현재가가_지정가_이상일_때_체결한다() {
            Order sell = accepted(OrderSide.SELL, OrderType.LIMIT, "80000", 10);

            assertThat(strategy.canExecute(sell, priceOf("80100"))).isTrue();
            assertThat(strategy.canExecute(sell, priceOf("80000"))).isTrue();
            assertThat(strategy.canExecute(sell, priceOf("79900"))).isFalse();
        }

        @Test
        @DisplayName("체결가는 지정가가 아니라 현재가다. 유리한 차액은 주문자 몫이다")
        void executesAtCurrentPriceNotLimitPrice() {
            Order buy = accepted(OrderSide.BUY, OrderType.LIMIT, "80000", 10);

            ExecutionResult result = strategy.execute(buy, priceOf("79000"));
            assertThat(result.price()).isEqualByComparingTo("79000");
            assertThat(result.amount()).isEqualByComparingTo("790000");
        }

        @Test
        void 가격_조건을_만족하지_않으면_체결을_거부한다() {
            Order buy = accepted(OrderSide.BUY, OrderType.LIMIT, "80000", 10);

            assertThatIllegalStateException().isThrownBy(() -> strategy.execute(buy, priceOf("80100")));
        }
    }
}
