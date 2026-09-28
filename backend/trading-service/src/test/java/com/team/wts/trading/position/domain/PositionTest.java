package com.team.wts.trading.position.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PositionTest {

    private static Position samsung() {
        return Position.open(1L, "005930");
    }

    @Test
    void 신규_포지션은_비어있다() {
        Position position = samsung();

        assertThat(position.quantity()).isZero();
        assertThat(position.availableQuantity()).isZero();
        assertThat(position.averagePrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("평균단가 계산 (CLAUDE.md §35)")
    void 매수할수록_평균단가가_이동한다() {
        Position position = samsung();
        position.add(10, new BigDecimal("80000"));
        assertThat(position.averagePrice()).isEqualByComparingTo(new BigDecimal("80000"));

        position.add(10, new BigDecimal("90000"));
        assertThat(position.quantity()).isEqualTo(20);
        assertThat(position.averagePrice()).isEqualByComparingTo(new BigDecimal("85000"));
    }

    @Test
    void 나누어떨어지지_않는_평균단가는_소수_넷째자리까지_반올림한다() {
        Position position = samsung();
        position.add(3, new BigDecimal("10000"));
        position.add(4, new BigDecimal("10001"));

        // (3*10000 + 4*10001) / 7 = 70004 / 7 = 10000.5714285...
        assertThat(position.averagePrice()).isEqualByComparingTo(new BigDecimal("10000.5714"));
    }

    @Test
    void 매도는_평균단가를_바꾸지_않는다() {
        Position position = samsung();
        position.add(10, new BigDecimal("80000"));
        position.reduce(4);

        assertThat(position.quantity()).isEqualTo(6);
        assertThat(position.averagePrice()).isEqualByComparingTo(new BigDecimal("80000"));
    }

    @Test
    void 전량_매도하면_평균단가를_초기화한다() {
        Position position = samsung();
        position.add(10, new BigDecimal("80000"));
        position.reduce(10);

        assertThat(position.quantity()).isZero();
        assertThat(position.averagePrice()).isEqualByComparingTo(BigDecimal.ZERO);

        // 다음 매수의 평균단가에 과거 단가가 섞이면 안 된다.
        position.add(5, new BigDecimal("50000"));
        assertThat(position.averagePrice()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("매도 가능 수량 = quantity - reserved_quantity (CLAUDE.md §9.3)")
    void 예약한_수량은_매도_가능_수량에서_빠진다() {
        Position position = samsung();
        position.add(10, new BigDecimal("80000"));
        position.reserve(4);

        assertThat(position.reservedQuantity()).isEqualTo(4);
        assertThat(position.availableQuantity()).isEqualTo(6);
    }

    @Test
    void 매도_가능_수량을_넘겨_예약할_수_없다() {
        Position position = samsung();
        position.add(10, new BigDecimal("80000"));
        position.reserve(10);

        assertThatIllegalStateException().isThrownBy(() -> position.reserve(1));
    }

    @Test
    void 예약을_풀면_다시_매도할_수_있다() {
        Position position = samsung();
        position.add(10, new BigDecimal("80000"));
        position.reserve(10);
        position.release(10);

        assertThat(position.availableQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("실현손익 = (체결가 - 평균단가) × 수량 (CLAUDE.md §35)")
    void 실현손익은_평균단가와의_차이다() {
        Position position = samsung();
        position.add(10, new BigDecimal("80000"));

        assertThat(position.realizedProfitOf(4, new BigDecimal("85000")))
                .isEqualByComparingTo("20000");
        assertThat(position.realizedProfitOf(4, new BigDecimal("75000")))
                .isEqualByComparingTo("-20000");
    }

    @Test
    @DisplayName("실현손익은 수량을 줄이기 전에 계산해야 한다")
    void 전량매도_후에는_실현손익을_계산할_수_없다() {
        Position position = samsung();
        position.add(10, new BigDecimal("80000"));
        BigDecimal realized = position.realizedProfitOf(10, new BigDecimal("85000"));
        position.reduce(10);

        assertThat(realized).isEqualByComparingTo("50000");
        // 평균단가가 0이 됐으므로 같은 계산을 다시 할 수 없다. 그래서 체결 시점에 저장한다.
        assertThat(position.averagePrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void 보유_수량보다_많이_줄일_수_없다() {
        Position position = samsung();
        position.add(10, new BigDecimal("80000"));

        assertThatIllegalStateException().isThrownBy(() -> position.reduce(11));
    }
}
