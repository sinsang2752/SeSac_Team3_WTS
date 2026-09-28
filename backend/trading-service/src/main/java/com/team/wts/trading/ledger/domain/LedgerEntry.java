package com.team.wts.trading.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 예수금이 움직인 기록. (CLAUDE.md §23 ledger_entries)
 *
 * <p>모든 잔고 변동에 한 줄씩 남는다. {@code amount}는 부호 있는 값이고
 * {@code after_balance = before_balance + amount}가 항상 성립한다. DB 제약으로도 강제한다.
 * 그래야 원장만으로 잔고를 재구성할 수 있다.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(name = "execution_id", updatable = false)
    private Long executionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false, updatable = false)
    private LedgerEntryType type;

    /** 입금은 양수, 출금은 음수. */
    @Column(name = "amount", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "before_balance", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal beforeBalance;

    @Column(name = "after_balance", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal afterBalance;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA 전용. */
    protected LedgerEntry() {
    }

    private LedgerEntry(Long accountId, Long orderId, Long executionId, LedgerEntryType type,
            BigDecimal amount, BigDecimal beforeBalance, BigDecimal afterBalance) {
        this.accountId = accountId;
        this.orderId = orderId;
        this.executionId = executionId;
        this.type = type;
        this.amount = amount;
        this.beforeBalance = beforeBalance;
        this.afterBalance = afterBalance;
    }

    public static LedgerEntry of(Long accountId, Long orderId, Long executionId,
            LedgerEntryType type, BigDecimal amount, BigDecimal beforeBalance,
            BigDecimal afterBalance) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(beforeBalance, "beforeBalance");
        Objects.requireNonNull(afterBalance, "afterBalance");
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("0원짜리 원장 기록은 남기지 않는다");
        }
        if (beforeBalance.add(amount).compareTo(afterBalance) != 0) {
            throw new IllegalArgumentException(
                    "잔고 계산이 맞지 않는다: " + beforeBalance + " + " + amount + " != " + afterBalance);
        }
        return new LedgerEntry(accountId, orderId, executionId, type, amount, beforeBalance,
                afterBalance);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long id() {
        return id;
    }

    public Long accountId() {
        return accountId;
    }

    public Long orderId() {
        return orderId;
    }

    public Long executionId() {
        return executionId;
    }

    public LedgerEntryType type() {
        return type;
    }

    public BigDecimal amount() {
        return amount;
    }

    public BigDecimal beforeBalance() {
        return beforeBalance;
    }

    public BigDecimal afterBalance() {
        return afterBalance;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
