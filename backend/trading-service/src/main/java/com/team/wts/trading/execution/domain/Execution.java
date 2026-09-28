package com.team.wts.trading.execution.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.team.wts.trading.order.domain.OrderSide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 체결 기록. (CLAUDE.md §23 executions)
 *
 * <p>주문의 {@code filled_quantity}만으로는 얼마에 체결됐는지 알 수 없다.
 * 체결가는 여기에만 남는다.
 *
 * @see #realizedProfit 매도 체결에만 값이 있다. 체결 시점 평균단가 기준이라 나중에 재계산할 수 없다.
 */
@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "symbol", length = 20, nullable = false, updatable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 4, nullable = false, updatable = false)
    private OrderSide side;

    @Column(name = "price", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false, updatable = false)
    private long quantity;

    @Column(name = "fee", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal fee;

    @Column(name = "realized_profit", precision = 19, scale = 4, updatable = false)
    private BigDecimal realizedProfit;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    /** JPA 전용. */
    protected Execution() {
    }

    private Execution(Long orderId, Long accountId, String symbol, OrderSide side, BigDecimal price,
            long quantity, BigDecimal fee, BigDecimal realizedProfit, Instant executedAt) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.fee = fee;
        this.realizedProfit = realizedProfit;
        this.executedAt = executedAt;
    }

    public static Execution of(Long orderId, Long accountId, String symbol, OrderSide side,
            BigDecimal price, long quantity, BigDecimal fee, BigDecimal realizedProfit,
            Instant executedAt) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(executedAt, "executedAt");
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("체결가는 0보다 커야 한다: " + price);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("체결수량은 1 이상이어야 한다: " + quantity);
        }
        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException("수수료는 0 이상이어야 한다: " + fee);
        }
        if (side == OrderSide.BUY && realizedProfit != null) {
            throw new IllegalArgumentException("매수 체결에는 실현손익이 없다");
        }
        return new Execution(orderId, accountId, symbol, side, price, quantity, fee, realizedProfit,
                executedAt);
    }

    /** 체결 대금. 수수료는 포함하지 않는다. */
    public BigDecimal amount() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public Long id() {
        return id;
    }

    public Long orderId() {
        return orderId;
    }

    public Long accountId() {
        return accountId;
    }

    public String symbol() {
        return symbol;
    }

    public OrderSide side() {
        return side;
    }

    public BigDecimal price() {
        return price;
    }

    public long quantity() {
        return quantity;
    }

    public BigDecimal fee() {
        return fee;
    }

    public BigDecimal realizedProfit() {
        return realizedProfit;
    }

    public Instant executedAt() {
        return executedAt;
    }
}
