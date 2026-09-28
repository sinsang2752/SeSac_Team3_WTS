package com.team.wts.market.stock.domain;

import java.util.List;
import java.util.Optional;

/** 종목 조회 포트. (CLAUDE.md §7) */
public interface StockRepository {

    List<Stock> findAll();

    Optional<Stock> findBySymbol(String symbol);

    /** 종목명 또는 종목코드 부분 일치 검색. (CLAUDE.md §28 – StockSearch) */
    List<Stock> search(String keyword);
}
