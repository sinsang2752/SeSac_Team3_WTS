package com.team.wts.market.candle.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.market.candle.domain.Candle;
import com.team.wts.market.support.Decimals;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param openTime 해당 분의 시작 시각(UTC). 프론트엔드가 Asia/Seoul로 표시한다 (CLAUDE.md §43).
 */
public record CandleResponse(
        @Schema(description = "봉 시작 시각 (UTC). 해당 분의 0초", example = "2026-09-22T00:01:00Z")
        Instant openTime,
        @Schema(description = "시가 (원)", example = "80300")
        BigDecimal open,
        @Schema(description = "고가 (원)", example = "80500")
        BigDecimal high,
        @Schema(description = "저가 (원)", example = "79000")
        BigDecimal low,
        @Schema(description = "종가 (원)", example = "79800")
        BigDecimal close,
        @Schema(description = "해당 1분 동안의 거래량 (주)", example = "158222")
        long volume) {

    public static CandleResponse from(Candle candle) {
        return new CandleResponse(candle.openTime(),
                Decimals.plain(candle.open()), Decimals.plain(candle.high()),
                Decimals.plain(candle.low()), Decimals.plain(candle.close()),
                candle.volume());
    }
}
