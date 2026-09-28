package com.team.wts.market.stock.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.market.stock.adapter.in.web.dto.StockResponse;
import com.team.wts.market.stock.application.StockQueryService;

/** 종목 API. (CLAUDE.md §24) */
@RestController
@RequestMapping("/api/market/stocks")
public class StockController {

    private final StockQueryService stocks;

    public StockController(StockQueryService stocks) {
        this.stocks = stocks;
    }

    /**
     * 종목 목록. {@code keyword}를 주면 종목코드/종목명 부분일치로 검색한다.
     * (CLAUDE.md §28 – StockSearch)
     */
    @GetMapping
    public List<StockResponse> list(@RequestParam(required = false) String keyword) {
        return stocks.search(keyword).stream().map(StockResponse::from).toList();
    }

    @GetMapping("/{symbol}")
    public StockResponse detail(@PathVariable String symbol) {
        return StockResponse.from(stocks.getBySymbol(symbol));
    }
}
