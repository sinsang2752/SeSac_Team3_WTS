package com.team.wts.trading.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

    @Test
    @DisplayName("출금은 음수 금액으로 남는다")
    void withdrawalIsNegative() {
        LedgerEntry entry = LedgerEntry.of(1L, 10L, 100L, LedgerEntryType.BUY,
                new BigDecimal("-800000"), new BigDecimal("100000000"), new BigDecimal("99200000"));

        assertThat(entry.amount()).isEqualByComparingTo("-800000");
        assertThat(entry.afterBalance()).isEqualByComparingTo("99200000");
    }

    @Test
    @DisplayName("after = before + amount 가 아니면 만들 수 없다")
    void rejectsInconsistentBalances() {
        assertThatIllegalArgumentException().isThrownBy(() -> LedgerEntry.of(1L, null, null,
                LedgerEntryType.SELL, new BigDecimal("1000"), new BigDecimal("100"),
                new BigDecimal("999")));
    }

    @Test
    @DisplayName("scale이 달라도 계산이 맞으면 통과한다")
    void acceptsDifferentScales() {
        // DB에서 DECIMAL(19,4)로 읽어오면 scale이 4가 된다.
        LedgerEntry entry = LedgerEntry.of(1L, null, null, LedgerEntryType.INITIAL_DEPOSIT,
                new BigDecimal("100000000.0000"), BigDecimal.ZERO, new BigDecimal("100000000"));

        assertThat(entry.type()).isEqualTo(LedgerEntryType.INITIAL_DEPOSIT);
    }

    @Test
    @DisplayName("0원짜리 기록은 남기지 않는다")
    void rejectsZeroAmount() {
        assertThatIllegalArgumentException().isThrownBy(() -> LedgerEntry.of(1L, null, null,
                LedgerEntryType.FEE, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("100")));
    }
}
