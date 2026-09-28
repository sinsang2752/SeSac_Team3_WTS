package com.team.wts.trading.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VirtualAccountTest {

    private static final String USER_ID = "3f1a6d2e-0c77-4a2f-9a1b-2b6d0a7e5c31";

    @Test
    @DisplayName("개설 직후에는 예약 금액이 0이고 전액 주문 가능하다")
    void opensWithFullAvailableCash() {
        VirtualAccount account = VirtualAccount.open(USER_ID, new BigDecimal("100000000"));

        assertThat(account.cashBalance()).isEqualByComparingTo("100000000");
        assertThat(account.reservedCash()).isEqualByComparingTo("0");
        assertThat(account.availableCash()).isEqualByComparingTo("100000000");
    }

    @Test
    @DisplayName("주문 가능 금액은 예수금에서 예약 금액을 뺀 값이다")
    void availableCashSubtractsReservedCash() {
        VirtualAccount account = VirtualAccount.open(USER_ID, new BigDecimal("100000000"));
        account.reserve(new BigDecimal("30000000"));

        assertThat(account.reservedCash()).isEqualByComparingTo("30000000");
        assertThat(account.availableCash()).isEqualByComparingTo("70000000");
        // 예약은 예수금을 줄이지 않는다. 묶어둘 뿐이다 (CLAUDE.md §9.2).
        assertThat(account.cashBalance()).isEqualByComparingTo("100000000");
    }

    @Test
    @DisplayName("초기 자금이 음수면 개설을 거부한다")
    void rejectsNegativeInitialCash() {
        assertThatThrownBy(() -> VirtualAccount.open(USER_ID, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("예약과 출납 (CLAUDE.md §9.2, §9.4)")
    class ReserveAndSettle {

        private VirtualAccount account() {
            return VirtualAccount.open(USER_ID, new BigDecimal("1000000"));
        }

        @Test
        @DisplayName("주문 가능 금액을 넘겨 예약할 수 없다")
        void cannotReserveBeyondAvailableCash() {
            VirtualAccount account = account();
            account.reserve(new BigDecimal("600000"));

            assertThatThrownBy(() -> account.reserve(new BigDecimal("500000")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("예약을 풀면 다시 주문할 수 있다")
        void releaseRestoresAvailableCash() {
            VirtualAccount account = account();
            account.reserve(new BigDecimal("600000"));
            account.release(new BigDecimal("600000"));

            assertThat(account.availableCash()).isEqualByComparingTo("1000000");
        }

        @Test
        @DisplayName("예약한 금액보다 많이 해제할 수 없다")
        void cannotReleaseMoreThanReserved() {
            VirtualAccount account = account();
            account.reserve(new BigDecimal("100000"));

            assertThatThrownBy(() -> account.release(new BigDecimal("100001")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("체결하면 예약을 풀고 체결 대금만 출금한다")
        void settlementReleasesReservationAndWithdrawsFillAmount() {
            VirtualAccount account = account();
            // 지정가 800,000원으로 예약했다가 750,000원에 체결된 경우.
            account.reserve(new BigDecimal("800000"));
            account.release(new BigDecimal("800000"));
            account.withdraw(new BigDecimal("750000"));

            assertThat(account.cashBalance()).isEqualByComparingTo("250000");
            assertThat(account.reservedCash()).isEqualByComparingTo("0");
            assertThat(account.availableCash()).isEqualByComparingTo("250000");
        }

        @Test
        @DisplayName("예수금은 음수가 될 수 없다 (CLAUDE.md §2)")
        void cannotWithdrawBeyondBalance() {
            VirtualAccount account = account();

            assertThatThrownBy(() -> account.withdraw(new BigDecimal("1000001")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("매도 대금은 예수금에 더해진다")
        void depositIncreasesCashBalance() {
            VirtualAccount account = account();
            account.deposit(new BigDecimal("250000"));

            assertThat(account.cashBalance()).isEqualByComparingTo("1250000");
        }

        @Test
        @DisplayName("0원 이하 금액은 다루지 않는다")
        void rejectsNonPositiveAmounts() {
            VirtualAccount account = account();

            assertThatThrownBy(() -> account.reserve(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> account.deposit(new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
