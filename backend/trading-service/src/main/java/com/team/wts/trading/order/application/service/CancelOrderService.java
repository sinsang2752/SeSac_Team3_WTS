package com.team.wts.trading.order.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.common.event.KafkaTopics;
import com.team.wts.trading.account.application.service.AccountService;
import com.team.wts.trading.account.domain.VirtualAccount;
import com.team.wts.trading.order.application.command.CancelOrderCommand;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderRepository;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.event.OrderEvents;
import com.team.wts.trading.outbox.application.OutboxRecorder;
import com.team.wts.trading.position.domain.PositionRepository;

/**
 * 주문 취소. (CLAUDE.md §24 – DELETE /api/trading/orders/{orderId})
 *
 * <p>미체결 주문만 취소된다. 이미 체결됐거나 취소된 주문은 {@code INVALID_ORDER_STATE}다
 * (§35 – 이미 체결된 주문 취소 거절). 취소는 되돌릴 게 예약뿐이라 거절을 기록으로 남기지 않고
 * 예외로 끝낸다.
 */
@Service
public class CancelOrderService {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderService.class);

    private final AccountService accountService;
    private final OrderRepository orders;
    private final PositionRepository positions;
    private final OutboxRecorder outbox;

    public CancelOrderService(AccountService accountService, OrderRepository orders,
            PositionRepository positions, OutboxRecorder outbox) {
        this.accountService = accountService;
        this.orders = orders;
        this.positions = positions;
        this.outbox = outbox;
    }

    @Transactional
    public Order cancel(CancelOrderCommand command) {
        VirtualAccount account = accountService.getOrOpenForUpdate(command.userId());

        // 남의 주문은 존재 자체를 알리지 않는다.
        Order order = orders.findByIdAndAccountId(command.orderId(), account.id())
                .orElseThrow(() -> new DomainException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.isCancellable()) {
            throw new DomainException(ErrorCode.INVALID_ORDER_STATE,
                    "이미 " + order.status() + " 상태인 주문은 취소할 수 없습니다.");
        }

        if (order.side() == OrderSide.BUY) {
            account.release(order.reservedCash());
        } else {
            long remaining = order.remainingQuantity();
            positions.findByAccountIdAndSymbol(account.id(), order.symbol())
                    .orElseThrow(() -> new IllegalStateException(
                            "미체결 매도 주문의 포지션이 없다: orderId=" + order.id()))
                    .release(remaining);
        }
        order.cancel();

        Order saved = orders.save(order);
        outbox.record("Order", String.valueOf(saved.id()), KafkaTopics.ORDER_CANCELLED,
                OrderEvents.OrderCancelled.from(saved));
        log.info("주문 취소: orderId={} accountId={} userId={} symbol={}",
                saved.id(), account.id(), account.userId(), saved.symbol());
        return saved;
    }
}
