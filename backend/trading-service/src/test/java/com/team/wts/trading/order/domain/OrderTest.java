package com.team.wts.trading.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.team.wts.common.error.ErrorCode;

class OrderTest {

    private static Order limitBuy(long quantity, String limitPrice) {
        return Order.receive(1L, "005930", OrderSide.BUY, OrderType.LIMIT,
                quantity, new BigDecimal(limitPrice), "key-1");
    }

    private static Order marketBuy(long quantity) {
        return Order.receive(1L, "005930", OrderSide.BUY, OrderType.MARKET,
                quantity, null, "key-1");
    }

    @Nested
    @DisplayName("주문 생성")
    class Receive {

        @Test
        void 접수된_주문은_RECEIVED다() {
            assertThat(marketBuy(10).status()).isEqualTo(OrderStatus.RECEIVED);
        }

        @Test
        void 수량이_0이하면_거부한다() {
            assertThatIllegalArgumentException().isThrownBy(() -> marketBuy(0));
        }

        @Test
        void 지정가_주문에_지정가가_없으면_거부한다() {
            assertThatIllegalArgumentException().isThrownBy(() -> Order.receive(
                    1L, "005930", OrderSide.BUY, OrderType.LIMIT, 10, null, "key-1"));
        }

        @Test
        void 시장가_주문에_지정가를_주면_거부한다() {
            assertThatIllegalArgumentException().isThrownBy(() -> Order.receive(
                    1L, "005930", OrderSide.BUY, OrderType.MARKET, 10,
                    new BigDecimal("80000"), "key-1"));
        }
    }

    @Nested
    @DisplayName("상태 머신 (CLAUDE.md §9.6)")
    class StateMachine {

        @Test
        void 검증_수락_체결_순으로_진행한다() {
            Order order = marketBuy(10);
            order.validate();
            assertThat(order.status()).isEqualTo(OrderStatus.VALIDATED);
            order.accept();
            assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
            order.fill(10);
            assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.filledQuantity()).isEqualTo(10);
            assertThat(order.remainingQuantity()).isZero();
        }

        @Test
        void 검증을_건너뛰고_수락할_수_없다() {
            assertThatIllegalStateException().isThrownBy(() -> marketBuy(10).accept());
        }

        @Test
        void 이미_체결된_주문은_취소할_수_없다() {
            Order order = marketBuy(10);
            order.validate();
            order.accept();
            order.fill(10);

            assertThat(order.isCancellable()).isFalse();
            assertThatIllegalStateException().isThrownBy(order::cancel);
        }

        @Test
        void 미체결_주문만_취소할_수_있다() {
            Order order = limitBuy(10, "80000");
            order.validate();
            order.accept();

            assertThat(order.isCancellable()).isTrue();
            order.cancel();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void 거절_사유를_남긴다() {
            Order order = marketBuy(10);
            order.reject(ErrorCode.INSUFFICIENT_BALANCE);

            assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
            assertThat(order.rejectReason()).isEqualTo("INSUFFICIENT_BALANCE");
            assertThat(order.status().isTerminal()).isTrue();
        }

        @Test
        void 부분체결은_지원하지_않는다() {
            Order order = marketBuy(10);
            order.validate();
            order.accept();

            assertThatIllegalArgumentException().isThrownBy(() -> order.fill(4));
        }
    }

    @Nested
    @DisplayName("예약 예수금")
    class ReservedCash {

        @Test
        void 지정가_매수는_지정가_곱하기_미체결수량이다() {
            assertThat(limitBuy(10, "80000").reservedCash())
                    .isEqualByComparingTo(new BigDecimal("800000"));
        }

        @Test
        void 매도_주문은_예수금을_묶지_않는다() {
            Order sell = Order.receive(1L, "005930", OrderSide.SELL, OrderType.LIMIT,
                    10, new BigDecimal("80000"), "key-1");
            assertThat(sell.reservedCash()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void 시장가_매수는_미체결로_남지_않으므로_예약액을_물으면_실패한다() {
            assertThatIllegalStateException().isThrownBy(() -> marketBuy(10).reservedCash());
        }
    }

    @Nested
    @DisplayName("Idempotency 요청 동일성 (CLAUDE.md §14)")
    class SameRequest {

        @Test
        void 모든_필드가_같으면_같은_요청이다() {
            assertThat(limitBuy(10, "80000").sameRequestAs(
                    "005930", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("80000")))
                    .isTrue();
        }

        @Test
        void 지정가의_scale이_달라도_같은_요청이다() {
            // DB에서 DECIMAL(19,4)로 읽어오면 80000.0000이 된다.
            assertThat(limitBuy(10, "80000.0000").sameRequestAs(
                    "005930", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("80000")))
                    .isTrue();
        }

        @Test
        void 수량이_다르면_다른_요청이다() {
            assertThat(limitBuy(10, "80000").sameRequestAs(
                    "005930", OrderSide.BUY, OrderType.LIMIT, 20, new BigDecimal("80000")))
                    .isFalse();
        }

        @Test
        void 시장가와_지정가는_다른_요청이다() {
            assertThat(marketBuy(10).sameRequestAs(
                    "005930", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("80000")))
                    .isFalse();
        }
    }
}
