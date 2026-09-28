package com.team.wts.market.quote.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.market.quote.domain.MarketPrice;
import com.team.wts.market.quote.domain.OrderBook;
import com.team.wts.market.quote.domain.QuoteCache;
import com.team.wts.market.stock.application.StockQueryService;

/** 최신 시세 조회. (CLAUDE.md §24) */
@Service
public class QuoteQueryService {

    private final QuoteCache cache;
    private final StockQueryService stocks;

    public QuoteQueryService(QuoteCache cache, StockQueryService stocks) {
        this.cache = cache;
        this.stocks = stocks;
    }

    /**
     * 최신 현재가. 없는 종목이면 404, 아직 시세가 안 들어왔으면 503.
     *
     * <p>둘을 구분하는 이유: 전자는 클라이언트가 고칠 문제이고,
     * 후자는 잠시 후 다시 시도하면 해소되는 상태다.
     */
    public MarketPrice getPrice(String symbol) {
        stocks.getBySymbol(symbol);
        return cache.findPrice(symbol)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.MARKET_PRICE_UNAVAILABLE,
                        "아직 시세가 수신되지 않았습니다: " + symbol));
    }

    public OrderBook getOrderBook(String symbol) {
        stocks.getBySymbol(symbol);
        return cache.findOrderBook(symbol)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.MARKET_PRICE_UNAVAILABLE,
                        "아직 호가가 수신되지 않았습니다: " + symbol));
    }

    /** 스냅샷 전송처럼 없으면 그냥 건너뛰어도 되는 경우에 쓴다. */
    public Optional<MarketPrice> findPrice(String symbol) {
        return cache.findPrice(symbol);
    }

    public Optional<OrderBook> findOrderBook(String symbol) {
        return cache.findOrderBook(symbol);
    }
}
