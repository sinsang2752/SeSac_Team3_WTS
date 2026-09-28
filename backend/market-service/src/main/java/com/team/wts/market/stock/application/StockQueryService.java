package com.team.wts.market.stock.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.market.stock.domain.Stock;
import com.team.wts.market.stock.domain.StockRepository;

/** 종목 조회. (CLAUDE.md §24) */
@Service
public class StockQueryService {

    private final StockRepository stocks;

    public StockQueryService(StockRepository stocks) {
        this.stocks = stocks;
    }

    public List<Stock> search(String keyword) {
        return stocks.search(keyword);
    }

    public Stock getBySymbol(String symbol) {
        return stocks.findBySymbol(symbol)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.SYMBOL_NOT_FOUND, "종목을 찾을 수 없습니다: " + symbol));
    }
}
