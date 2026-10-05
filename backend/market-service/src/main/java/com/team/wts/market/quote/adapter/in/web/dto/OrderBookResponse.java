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
        String symbol,
        List<Level> asks,
        List<Level> bids,
        Instant timestamp) {

    @Schema(name = "OrderBookLevel")
    public record Level(BigDecimal price, long quantity) {
    }

    public static OrderBookResponse from(OrderBook source) {
        return new OrderBookResponse(
                source.symbol(),
                source.asks().stream().map(l -> new Level(l.price(), l.quantity())).toList(),
                source.bids().stream().map(l -> new Level(l.price(), l.quantity())).toList(),
                source.timestamp());
    }
}
