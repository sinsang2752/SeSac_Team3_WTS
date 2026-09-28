package com.team.wts.trading.order.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderStatus;
import com.team.wts.trading.order.domain.OrderType;

/**
 * 주문 수명주기 이벤트 payload. (CLAUDE.md §16)
 *
 * <p>세 이벤트가 같은 모양이라 한 파일에 모았다. 형태가 갈라지면 나눈다.
 */
public final class OrderEvents {

    /** {@code order.created} – 주문이 저장됐다. 거절된 주문도 포함한다. */
    public record OrderCreated(
            Long orderId,
            Long accountId,
            String symbol,
            OrderSide side,
            OrderType orderType,
            long quantity,
            BigDecimal limitPrice,
            OrderStatus status,
            String rejectReason,
            Instant createdAt) {

        public static OrderCreated from(Order order) {
            return new OrderCreated(order.id(), order.accountId(), order.symbol(), order.side(),
                    order.orderType(), order.quantity(), order.limitPrice(), order.status(),
                    order.rejectReason(), order.createdAt());
        }
    }

    /** {@code order.accepted} – 검증을 통과하고 예수금 또는 수량을 묶었다. */
    public record OrderAccepted(
            Long orderId,
            Long accountId,
            String symbol,
            OrderSide side,
            OrderType orderType,
            long quantity,
            BigDecimal limitPrice,
            /** 이 주문이 묶은 예수금. 매도 주문은 0이다. */
            BigDecimal reservedCash) {

        public static OrderAccepted of(Order order, BigDecimal reservedCash) {
            return new OrderAccepted(order.id(), order.accountId(), order.symbol(), order.side(),
                    order.orderType(), order.quantity(), order.limitPrice(), reservedCash);
        }
    }

    /** {@code order.cancelled} – 미체결 주문을 취소하고 예약을 풀었다. */
    public record OrderCancelled(
            Long orderId,
            Long accountId,
            String symbol,
            OrderSide side,
            long cancelledQuantity,
            Instant cancelledAt) {

        public static OrderCancelled from(Order order) {
            return new OrderCancelled(order.id(), order.accountId(), order.symbol(), order.side(),
                    order.remainingQuantity(), order.updatedAt());
        }
    }

    private OrderEvents() {
    }
}
