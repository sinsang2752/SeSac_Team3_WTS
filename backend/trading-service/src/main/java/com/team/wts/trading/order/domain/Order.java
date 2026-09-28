package com.team.wts.trading.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.team.wts.common.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 주문 애그리거트. (CLAUDE.md §23 orders, §9.6 상태 머신)
 *
 * <p>상태 전이는 전부 이 클래스의 메서드를 거친다. 허용되지 않은 전이는
 * {@link IllegalStateException}이다. 사용자 입력 오류가 아니라 서비스 코드의 버그이기 때문이다.
 * 사용자가 유발할 수 있는 거절은 {@link #reject(ErrorCode)}로 기록한다.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "symbol", length = 20, nullable = false, updatable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 4, nullable = false, updatable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 8, nullable = false, updatable = false)
    private OrderType orderType;

    @Column(name = "quantity", nullable = false, updatable = false)
    private long quantity;

    /** 지정가에서만 값이 있다. 시장가는 null이다. */
    @Column(name = "limit_price", precision = 19, scale = 4, updatable = false)
    private BigDecimal limitPrice;

    @Column(name = "filled_quantity", nullable = false)
    private long filledQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private OrderStatus status;

    /**
     * 거절 사유 {@link ErrorCode} 이름. CLAUDE.md §23 스키마에는 없는 컬럼이다.
     * REJECTED 주문을 남기면서 이유를 적지 않으면 주문 내역에서 아무것도 설명할 수 없어 추가했다.
     */
    @Column(name = "reject_reason", length = 40)
    private String rejectReason;

    @Column(name = "idempotency_key", length = 64, nullable = false, updatable = false)
    private String idempotencyKey;

    // Instant 기본 매핑이 MySQL에서 timestamp가 되어 DDL의 datetime(6)과 어긋난다.
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA 전용. */
    protected Order() {
    }

    private Order(Long accountId, String symbol, OrderSide side, OrderType orderType,
            long quantity, BigDecimal limitPrice, String idempotencyKey) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.idempotencyKey = idempotencyKey;
        this.filledQuantity = 0L;
        this.status = OrderStatus.RECEIVED;
    }

    /** 주문을 접수한다. 아직 검증 전이다. */
    public static Order receive(Long accountId, String symbol, OrderSide side, OrderType orderType,
            long quantity, BigDecimal limitPrice, String idempotencyKey) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(orderType, "orderType");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 한다: " + quantity);
        }
        if (orderType == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("지정가 주문에는 0보다 큰 지정가가 필요하다: " + limitPrice);
        }
        if (orderType == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException("시장가 주문에는 지정가를 줄 수 없다: " + limitPrice);
        }
        return new Order(accountId, symbol, side, orderType, quantity, limitPrice, idempotencyKey);
    }

    /** 자금/수량 검증을 통과했다. */
    public void validate() {
        transitionFrom(OrderStatus.RECEIVED, OrderStatus.VALIDATED);
    }

    /** 예수금 또는 보유수량 예약을 마치고 주문이 살아 있는 상태가 됐다. */
    public void accept() {
        transitionFrom(OrderStatus.VALIDATED, OrderStatus.ACCEPTED);
    }

    /** 전량 체결한다. MVP는 부분체결을 만들지 않는다 (§9.6). */
    public void fill(long executedQuantity) {
        if (executedQuantity != remainingQuantity()) {
            throw new IllegalArgumentException(
                    "MVP는 전량 체결만 지원한다: executed=" + executedQuantity + " remaining=" + remainingQuantity());
        }
        transitionFrom(OrderStatus.ACCEPTED, OrderStatus.FILLED);
        this.filledQuantity += executedQuantity;
    }

    /** 미체결 주문을 취소한다. */
    public void cancel() {
        transitionFrom(OrderStatus.ACCEPTED, OrderStatus.CANCELLED);
    }

    /** 검증 단계에서 거절한다. 거절된 주문도 기록으로 남긴다. */
    public void reject(ErrorCode reason) {
        Objects.requireNonNull(reason, "reason");
        transitionFrom(OrderStatus.RECEIVED, OrderStatus.REJECTED);
        this.rejectReason = reason.name();
    }

    private void transitionFrom(OrderStatus expected, OrderStatus next) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "허용되지 않은 주문 상태 전이: " + this.status + " -> " + next + " (기대 상태 " + expected + ")");
        }
        this.status = next;
    }

    /** 취소할 수 있는가. 미체결 주문만 취소된다 (§35 – 이미 체결된 주문 취소 거절). */
    public boolean isCancellable() {
        return status.isOpen();
    }

    public long remainingQuantity() {
        return quantity - filledQuantity;
    }

    /**
     * 이 주문이 묶어 둔 예수금. 매수 주문에만 의미가 있다.
     *
     * <p>별도 컬럼을 두지 않고 계산한다. 미체결로 남는 주문은 지정가뿐이고,
     * 지정가 매수의 예약액은 언제나 {@code limitPrice × 미체결수량}이기 때문이다 (§9.4).
     */
    public BigDecimal reservedCash() {
        if (side != OrderSide.BUY) {
            return BigDecimal.ZERO;
        }
        if (limitPrice == null) {
            throw new IllegalStateException("시장가 매수 주문은 예약을 남기지 않는다: orderId=" + id);
        }
        return limitPrice.multiply(BigDecimal.valueOf(remainingQuantity()));
    }

    /** 같은 Idempotency-Key로 들어온 요청이 정말 같은 주문인지 확인한다 (§14). */
    public boolean sameRequestAs(String otherSymbol, OrderSide otherSide, OrderType otherType,
            long otherQuantity, BigDecimal otherLimitPrice) {
        return this.symbol.equals(otherSymbol)
                && this.side == otherSide
                && this.orderType == otherType
                && this.quantity == otherQuantity
                && sameLimitPrice(otherLimitPrice);
    }

    private boolean sameLimitPrice(BigDecimal other) {
        if (limitPrice == null || other == null) {
            return limitPrice == null && other == null;
        }
        // DECIMAL(19,4)로 읽어오면 scale이 달라진다. 값만 비교한다.
        return limitPrice.compareTo(other) == 0;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long id() {
        return id;
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

    public OrderType orderType() {
        return orderType;
    }

    public long quantity() {
        return quantity;
    }

    public BigDecimal limitPrice() {
        return limitPrice;
    }

    public long filledQuantity() {
        return filledQuantity;
    }

    public OrderStatus status() {
        return status;
    }

    public String rejectReason() {
        return rejectReason;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
