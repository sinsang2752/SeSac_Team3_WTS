package com.team.wts.market.quote.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.team.wts.market.quote.domain.OrderBook;

/**
 * @param asks 매도호가. 최우선(가장 낮은 가격)부터.
 * @param bids 매수호가. 최우선(가장 높은 가격)부터.
 */
import io.swagger.v3.oas.annotations.media.Schema;

public record OrderBookResponse(
        @Schema(description = "종목코드", example = "005930")
        String symbol,
        @Schema(description = "매도호가. 최우선(가장 낮은 가격)부터")
        List<Level> asks,
        @Schema(description = "매수호가. 최우선(가장 높은 가격)부터")
        List<Level> bids,
        @Schema(description = "호가 시각 (UTC, ISO-8601)", example = "2026-09-22T08:12:44.101Z")
        Instant timestamp) {

    @Schema(name = "OrderBookLevel")
    public record Level(
            @Schema(description = "호가 (원)", example = "79900") BigDecimal price,
            @Schema(description = "잔량 (주)", example = "835") long quantity) {
    }

    public static OrderBookResponse from(OrderBook source) {
        return new OrderBookResponse(
                source.symbol(),
                source.asks().stream().map(l -> new Level(l.price(), l.quantity())).toList(),
                source.bids().stream().map(l -> new Level(l.price(), l.quantity())).toList(),
                source.timestamp());
    }
}
