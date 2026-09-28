package com.team.wts.trading.order.domain;

import java.util.List;
import java.util.Optional;

/** 주문 영속화 포트. (CLAUDE.md §7) */
public interface OrderRepository {

    Order save(Order order);

    java.util.Optional<Order> findById(Long id);

    /**
     * 체결을 기다리는 지정가 주문 ID. 오래된 주문부터.
     *
     * <p>ID만 읽는다. 실제 처리는 계좌 락을 잡은 뒤 각자의 트랜잭션에서 다시 읽는다.
     */
    List<Long> findOpenLimitOrderIds(String symbol);

    Optional<Order> findByIdAndAccountId(Long id, Long accountId);

    Optional<Order> findByAccountIdAndIdempotencyKey(Long accountId, String idempotencyKey);

    List<Order> findByAccountIdOrderByIdDesc(Long accountId);

    List<Order> findByAccountIdAndStatusOrderByIdDesc(Long accountId, OrderStatus status);
}
