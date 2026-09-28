package com.team.wts.market.stock.adapter.in.web.dto;

import com.team.wts.market.stock.domain.Stock;

/**
 * 종목 마스터 응답. (CLAUDE.md §24 – GET /api/market/stocks)
 *
 * <p>전일 종가는 여기 없다. 종목 마스터가 아니라 <b>시세</b>에 속한 값이고,
 * 설정의 {@code previous-close}는 Mock 모드의 출발 가격일 뿐이다.
 * kis 모드에서 그 값을 내보내면 실제와 전혀 다른 숫자가 나간다.
 * 전일 종가는 {@code GET /api/market/stocks/{symbol}/price} 가 준다.
 */
public record StockResponse(String symbol, String name, String market) {

    public static StockResponse from(Stock stock) {
        return new StockResponse(stock.symbol(), stock.name(), stock.market());
    }
}
