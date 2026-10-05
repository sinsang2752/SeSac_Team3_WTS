package com.team.wts.user.watchlist.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WatchlistItemTest {

    private static final String USER_ID = "3f1a6d2e-0c77-4a2f-9a1b-2b6d0a7e5c31";

    @Test
    @DisplayName("종목코드는 6자리 숫자여야 한다")
    void requiresSixDigitSymbol() {
        assertThat(WatchlistItem.of(USER_ID, "005930").symbol()).isEqualTo("005930");

        assertThatIllegalArgumentException().isThrownBy(() -> WatchlistItem.of(USER_ID, "5930"));
        assertThatIllegalArgumentException().isThrownBy(() -> WatchlistItem.of(USER_ID, "SAMSUNG"));
        assertThatIllegalArgumentException().isThrownBy(() -> WatchlistItem.of(USER_ID, "0059300"));
    }

    @Test
    @DisplayName("형식 검사는 예외 없이도 물어볼 수 있다")
    void exposesValidationWithoutThrowing() {
        assertThat(WatchlistItem.isValidSymbol("000660")).isTrue();
        // 2024년부터 영문이 섞인 종목코드가 상장된다.
        assertThat(WatchlistItem.isValidSymbol("0001A0")).isTrue();
        assertThat(WatchlistItem.isValidSymbol("abc")).isFalse();
        assertThat(WatchlistItem.isValidSymbol(null)).isFalse();
    }
}
