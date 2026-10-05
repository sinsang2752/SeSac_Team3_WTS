package com.team.wts.trading.order.adapter.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderRepository;
import com.team.wts.trading.order.domain.OrderStatus;
import com.team.wts.trading.order.domain.OrderType;

public interface OrderJpaRepository extends OrderRepository, JpaRepository<Order, Long> {

    @Query("""
            select o.id from Order o
            where o.symbol = :symbol
              and o.status = :status
              and o.orderType = :orderType
            order by o.id
            """)
    List<Long> findOpenLimitOrderIds(@Param("symbol") String symbol,
            @Param("status") OrderStatus status, @Param("orderType") OrderType orderType);

    @Override
    default List<Long> findOpenLimitOrderIds(String symbol) {
        return findOpenLimitOrderIds(symbol, OrderStatus.ACCEPTED, OrderType.LIMIT);
    }
}
