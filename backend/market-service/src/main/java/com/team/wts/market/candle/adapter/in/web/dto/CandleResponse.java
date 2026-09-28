package com.team.wts.market.candle.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.market.candle.domain.Candle;

/**
 * @param openTime 해당 분의 시작 시각(UTC). 프론트엔드가 Asia/Seoul로 표시한다 (CLAUDE.md §43).
 */
public record CandleResponse(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {

    public static CandleResponse from(Candle candle) {
        return new CandleResponse(candle.openTime(),
                normalize(candle.open()), normalize(candle.high()),
                normalize(candle.low()), normalize(candle.close()),
                candle.volume());
    }

    /**
     * 소수 자릿수를 맞춘다.
     *
     * <p>DB에서 읽은 봉은 DECIMAL(19,4)라 80300.0000으로, 아직 확정되지 않은 현재 봉은
     * 메모리의 값 그대로 80300으로 나간다. 한 응답 배열 안에서 형식이 갈리면 클라이언트가
     * 쓰기 불편하므로 최소 표현으로 통일한다.
     */
    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        // stripTrailingZeros는 80300을 8.03E+4(scale -2)로 만들 수 있다. 지수 표기를 피한다.
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
