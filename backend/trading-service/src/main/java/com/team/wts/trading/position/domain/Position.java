package com.team.wts.trading.position.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 종목별 보유 포지션. (CLAUDE.md §23 positions, §9.3)
 *
 * <p>평균단가는 매수로만 바뀐다. 매도는 수량만 줄이고 단가는 건드리지 않는다.
 * 이동평균법 기준이며, 실현손익은 {@code average_price}와 매도가의 차이로 Phase 4의
 * Ledger에서 계산한다.
 */
@Entity
@Table(name = "positions")
public class Position {

    /** 평균단가 소수 자리. DDL의 DECIMAL(19,4)와 맞춘다. */
    private static final int PRICE_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "symbol", length = 20, nullable = false, updatable = false)
    private String symbol;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    /** 미체결 매도 주문이 묶어둔 수량. */
    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @Column(name = "average_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal averagePrice;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** JPA 전용. */
    protected Position() {
    }

    private Position(Long accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = 0L;
        this.reservedQuantity = 0L;
        this.averagePrice = BigDecimal.ZERO;
    }

    /** 아직 보유하지 않은 종목의 빈 포지션을 만든다. */
    public static Position open(Long accountId, String symbol) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(symbol, "symbol");
        return new Position(accountId, symbol);
    }

    /**
     * 매도 가능 수량. (CLAUDE.md §9.3)
     *
     * <pre>available_quantity = quantity - reserved_quantity</pre>
     */
    public long availableQuantity() {
        return quantity - reservedQuantity;
    }

    /** 매도 주문이 수량을 묶는다. */
    public void reserve(long amount) {
        requirePositive(amount);
        if (availableQuantity() < amount) {
            throw new IllegalStateException(
                    "매도 가능 수량 부족: available=" + availableQuantity() + " requested=" + amount);
        }
        this.reservedQuantity += amount;
    }

    /** 묶어둔 수량을 푼다. 체결되거나 주문이 취소될 때다. */
    public void release(long amount) {
        requirePositive(amount);
        if (reservedQuantity < amount) {
            throw new IllegalStateException(
                    "예약 수량보다 많이 해제할 수 없다: reserved=" + reservedQuantity + " requested=" + amount);
        }
        this.reservedQuantity -= amount;
    }

    /** 매수 체결. 수량을 늘리고 평균단가를 다시 계산한다 (§35 – 평균단가 계산). */
    public void add(long executedQuantity, BigDecimal executedPrice) {
        requirePositive(executedQuantity);
        Objects.requireNonNull(executedPrice, "executedPrice");

        BigDecimal previousAmount = averagePrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal addedAmount = executedPrice.multiply(BigDecimal.valueOf(executedQuantity));
        long newQuantity = quantity + executedQuantity;

        this.averagePrice = previousAmount.add(addedAmount)
                .divide(BigDecimal.valueOf(newQuantity), PRICE_SCALE, RoundingMode.HALF_UP);
        this.quantity = newQuantity;
    }

    /**
     * 이 수량을 지금 가격에 팔면 실현되는 손익. (CLAUDE.md §35 – 매도 후 실현손익 계산)
     *
     * <pre>(체결가 - 평균단가) × 수량</pre>
     *
     * <p>반드시 {@link #reduce}를 부르기 <b>전</b>에 계산해야 한다.
     * 전량 매도하면 평균단가가 0으로 초기화되기 때문이다.
     */
    public BigDecimal realizedProfitOf(long executedQuantity, BigDecimal executedPrice) {
        requirePositive(executedQuantity);
        Objects.requireNonNull(executedPrice, "executedPrice");
        if (quantity < executedQuantity) {
            throw new IllegalStateException(
                    "보유 수량보다 많이 팔 수 없다: quantity=" + quantity + " requested=" + executedQuantity);
        }
        return executedPrice.subtract(averagePrice).multiply(BigDecimal.valueOf(executedQuantity));
    }

    /** 매도 체결. 평균단가는 그대로 둔다. */
    public void reduce(long executedQuantity) {
        requirePositive(executedQuantity);
        if (quantity < executedQuantity) {
            throw new IllegalStateException(
                    "보유 수량보다 많이 줄일 수 없다: quantity=" + quantity + " requested=" + executedQuantity);
        }
        this.quantity -= executedQuantity;
        if (this.quantity == 0L) {
            // 전량 매도했다. 행은 남기되 단가는 초기화한다.
            // 다음 매수의 평균단가 계산에 과거 단가가 섞이면 안 된다.
            this.averagePrice = BigDecimal.ZERO;
        }
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 한다: " + amount);
        }
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

    public long quantity() {
        return quantity;
    }

    public long reservedQuantity() {
        return reservedQuantity;
    }

    public BigDecimal averagePrice() {
        return averagePrice;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
