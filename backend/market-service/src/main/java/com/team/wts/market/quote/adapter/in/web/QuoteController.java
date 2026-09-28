package com.team.wts.market.quote.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.market.quote.adapter.in.web.dto.OrderBookResponse;
import com.team.wts.market.quote.adapter.in.web.dto.PriceResponse;
import com.team.wts.market.quote.application.QuoteQueryService;

/** 시세 API. (CLAUDE.md §24) */
@RestController
@RequestMapping("/api/market/stocks/{symbol}")
public class QuoteController {

    private final QuoteQueryService quotes;

    public QuoteController(QuoteQueryService quotes) {
        this.quotes = quotes;
    }

    @GetMapping("/price")
    public PriceResponse price(@PathVariable String symbol) {
        return PriceResponse.from(quotes.getPrice(symbol));
    }

    @GetMapping("/orderbook")
    public OrderBookResponse orderBook(@PathVariable String symbol) {
        return OrderBookResponse.from(quotes.getOrderBook(symbol));
    }
}
