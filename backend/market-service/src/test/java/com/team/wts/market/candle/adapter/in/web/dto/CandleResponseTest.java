package com.team.wts.market.candle.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team.wts.market.candle.domain.Candle;

class CandleResponseTest {

    private static final Instant OPEN_TIME = Instant.parse("2026-09-21T01:30:00Z");

    @Test
    @DisplayName("DB에서 읽은 봉과 메모리의 봉이 같은 숫자 형식으로 나간다")
    void normalizesScaleConsistently() {
        // DB는 DECIMAL(19,4)라 scale 4로 올라온다.
        CandleResponse fromDatabase = CandleResponse.from(candle(new BigDecimal("80300.0000")));
        // 진행 중인 봉은 시세 그대로 scale 0이다.
        CandleResponse inMemory = CandleResponse.from(candle(new BigDecimal("80300")));

        assertThat(fromDatabase.open().toPlainString()).isEqualTo(inMemory.open().toPlainString());
        assertThat(fromDatabase.open().toPlainString()).isEqualTo("80300");
    }

    @Test
    @DisplayName("지수 표기로 새지 않는다")
    void neverUsesScientificNotation() {
        CandleResponse response = CandleResponse.from(candle(new BigDecimal("1200000.0000")));

        assertThat(response.open().toString()).isEqualTo("1200000");
    }

    @Test
    @DisplayName("소수가 있는 값은 유효 자릿수를 잃지 않는다")
    void keepsMeaningfulDecimals() {
        CandleResponse response = CandleResponse.from(candle(new BigDecimal("1234.5600")));

        assertThat(response.open()).isEqualByComparingTo("1234.56");
    }

    private static Candle candle(BigDecimal price) {
        return new Candle("005930", OPEN_TIME, price, price, price, price, 1_000L);
    }
}
