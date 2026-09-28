package com.team.wts.trading.order.application.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.trading.account.domain.VirtualAccount;
import com.team.wts.trading.account.domain.VirtualAccountRepository;
import com.team.wts.trading.execution.application.service.ExecutionService;
import com.team.wts.trading.market.domain.MarketPrice;
import com.team.wts.trading.order.application.execution.LimitOrderExecutionStrategy;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderRepository;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.position.domain.Position;
import com.team.wts.trading.position.domain.PositionRepository;

/**
 * 시세가 조건에 도달한 미체결 지정가 주문을 체결한다. (CLAUDE.md §12, §47 Phase 4)
 *
 * <p>주문 한 건마다 트랜잭션을 나눈다. 한 계좌의 체결이 실패해도 다른 계좌의 체결은 살아남고,
 * 계좌 락을 짧게 잡는다.
 *
 * <p><b>중복 이벤트는 주문 상태가 막는다.</b> 같은 시세 이벤트가 두 번 와도 이미 FILLED인
 * 주문은 {@link #execute}가 그냥 넘어간다. 별도의 {@code processed_events} 테이블이 없는
 * 이유다 (§17). 이벤트가 아니라 주문 상태를 기준으로 판단하므로, 서로 다른 두 이벤트가
 * 같은 주문을 두 번 체결하는 것도 함께 막힌다.
 */
@Service
public class PendingOrderExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExecutionService.class);

    private final VirtualAccountRepository accounts;
    private final OrderRepository orders;
    private final PositionRepository positions;
    private final ExecutionService executionService;
    private final LimitOrderExecutionStrategy limitStrategy;

    public PendingOrderExecutionService(VirtualAccountRepository accounts, OrderRepository orders,
            PositionRepository positions, ExecutionService executionService,
            LimitOrderExecutionStrategy limitStrategy) {
        this.accounts = accounts;
        this.orders = orders;
        this.positions = positions;
        this.executionService = executionService;
        this.limitStrategy = limitStrategy;
    }

    /**
     * 체결 후보 주문 ID. 락 없이 읽는다.
     *
     * <p>여기서 고른 주문이 {@link #execute} 시점에 이미 취소됐을 수 있다.
     * 그래서 락을 잡은 뒤 상태와 가격 조건을 다시 본다.
     */
    @Transactional(readOnly = true)
    public List<Long> findCandidateOrderIds(String symbol) {
        return orders.findOpenLimitOrderIds(symbol);
    }

    /** @return 체결했으면 true */
    @Transactional
    public boolean execute(Long orderId, MarketPrice price) {
        Order candidate = orders.findById(orderId).orElse(null);
        if (candidate == null || !candidate.status().isOpen()) {
            return false;
        }

        // 계좌를 먼저 잠근다 (CLAUDE.md §13, ADR-0009).
        VirtualAccount account = accounts.findByIdForUpdate(candidate.accountId()).orElse(null);
        if (account == null) {
            log.error("주문의 계좌가 없다: orderId={} accountId={}", orderId, candidate.accountId());
            return false;
        }

        // 락을 잡기 전에 읽은 상태다. 다시 확인한다.
        Order order = orders.findById(orderId).orElseThrow();
        if (!order.status().isOpen() || !limitStrategy.canExecute(order, price)) {
            return false;
        }

        Position position = positions.findByAccountIdAndSymbol(account.id(), order.symbol())
                .orElseGet(() -> Position.open(account.id(), order.symbol()));

        if (order.side() == OrderSide.BUY) {
            account.release(order.reservedCash());
        } else {
            position.release(order.remainingQuantity());
        }
        executionService.settle(account, order, position, limitStrategy.execute(order, price));
        orders.save(order);

        log.info("미체결 지정가 체결: orderId={} accountId={} symbol={} limitPrice={} 체결가={}",
                order.id(), account.id(), order.symbol(), order.limitPrice(), price.price());
        return true;
    }
}
