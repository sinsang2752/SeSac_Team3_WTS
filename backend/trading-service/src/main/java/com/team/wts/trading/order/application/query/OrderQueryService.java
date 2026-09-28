package com.team.wts.trading.order.application.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.trading.account.application.service.AccountService;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderRepository;
import com.team.wts.trading.order.domain.OrderStatus;

/** 주문 조회. (CLAUDE.md §45 – Query 분리, 별도 Read DB는 만들지 않는다) */
@Service
public class OrderQueryService {

    private final AccountService accountService;
    private final OrderRepository orders;

    public OrderQueryService(AccountService accountService, OrderRepository orders) {
        this.accountService = accountService;
        this.orders = orders;
    }

    /** 최근 주문부터. {@code status}가 있으면 그 상태만 (미체결 목록 용도). */
    /**
     * {@code readOnly = true}를 쓰지 않는다.
     *
     * <p>계좌는 최초 조회 시점에 만들어진다(ADR-0005). 읽기 전용 트랜잭션은 JDBC 커넥션을
     * read-only로 열기 때문에 그 INSERT가 실패한다. 조회지만 쓰기가 일어날 수 있는 경로다.
     */
    @Transactional
    public List<Order> findAll(String userId, OrderStatus status) {
        Long accountId = accountService.getOrOpen(userId).id();
        return status == null
                ? orders.findByAccountIdOrderByIdDesc(accountId)
                : orders.findByAccountIdAndStatusOrderByIdDesc(accountId, status);
    }

    @Transactional
    public Order findOne(String userId, Long orderId) {
        Long accountId = accountService.getOrOpen(userId).id();
        return orders.findByIdAndAccountId(orderId, accountId)
                .orElseThrow(() -> new DomainException(ErrorCode.ORDER_NOT_FOUND));
    }
}
