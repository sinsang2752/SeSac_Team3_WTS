package com.team.wts.trading.account.domain;

import java.math.BigDecimal;
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
 * 가상 계좌 애그리거트. (CLAUDE.md §23 virtual_accounts, §9.2)
 *
 * <p>금액은 전부 {@link BigDecimal}이다. 금융 계산에 부동소수를 쓰지 않는다 (§42).
 */
@Entity
@Table(name = "virtual_accounts")
public class VirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Column(name = "cash_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal cashBalance;

    /** 미체결 매수 주문이 묶어둔 금액. */
    @Column(name = "reserved_cash", precision = 19, scale = 4, nullable = false)
    private BigDecimal reservedCash;

    // Instant 기본 매핑이 MySQL에서 timestamp가 되어 DDL의 datetime(6)과 어긋난다.
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 낙관적 락 (CLAUDE.md §13).
     *
     * <p>주문 처리는 이 행을 비관적 락으로 잡고 진행하므로(ADR-0009) 여기서 충돌이 나는 일은
     * 사실상 없다. 락을 잡지 않는 경로가 실수로 잔고를 건드리면 드러나게 하는 안전망이다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** JPA 전용. */
    protected VirtualAccount() {
    }

    private VirtualAccount(String userId, BigDecimal initialCash) {
        this.userId = userId;
        this.cashBalance = initialCash;
        this.reservedCash = BigDecimal.ZERO;
    }

    /** 신규 계좌를 개설하고 초기 가상자금을 넣는다. */
    public static VirtualAccount open(String userId, BigDecimal initialCash) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(initialCash, "initialCash");
        if (initialCash.signum() < 0) {
            throw new IllegalArgumentException("초기 자금은 음수일 수 없다: " + initialCash);
        }
        return new VirtualAccount(userId, initialCash);
    }

    /**
     * 주문 가능 금액. (CLAUDE.md §9.2)
     *
     * <pre>available_cash = cash_balance - reserved_cash</pre>
     */
    public BigDecimal availableCash() {
        return cashBalance.subtract(reservedCash);
    }

    /**
     * 매수 주문이 예수금을 묶는다. (CLAUDE.md §9.2, §9.4)
     *
     * <p>호출 전에 {@link #availableCash()}로 확인하는 것이 정상 흐름이다.
     * 여기서 던지는 예외는 그 확인을 빠뜨렸다는 뜻이다.
     */
    public void reserve(BigDecimal amount) {
        requirePositive(amount);
        if (availableCash().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "주문 가능 금액 부족: available=" + availableCash() + " requested=" + amount);
        }
        this.reservedCash = this.reservedCash.add(amount);
    }

    /** 묶어둔 금액을 푼다. 체결되거나 주문이 취소될 때다. */
    public void release(BigDecimal amount) {
        requirePositive(amount);
        if (reservedCash.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "예약 금액보다 많이 해제할 수 없다: reserved=" + reservedCash + " requested=" + amount);
        }
        this.reservedCash = this.reservedCash.subtract(amount);
    }

    /**
     * 매수 체결 대금을 출금한다.
     *
     * <p>예약 해제 뒤에 부른다. 예수금은 음수가 될 수 없다 (CLAUDE.md §2).
     */
    public void withdraw(BigDecimal amount) {
        requirePositive(amount);
        if (cashBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "예수금 부족: balance=" + cashBalance + " requested=" + amount);
        }
        this.cashBalance = this.cashBalance.subtract(amount);
    }

    /** 매도 체결 대금을 입금한다. */
    public void deposit(BigDecimal amount) {
        requirePositive(amount);
        this.cashBalance = this.cashBalance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 한다: " + amount);
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

    public String userId() {
        return userId;
    }

    public BigDecimal cashBalance() {
        return cashBalance;
    }

    public BigDecimal reservedCash() {
        return reservedCash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
