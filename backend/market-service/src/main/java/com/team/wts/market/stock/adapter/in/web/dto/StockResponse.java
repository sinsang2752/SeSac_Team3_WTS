package com.team.wts.market.stock.adapter.in.web.dto;

import com.team.wts.market.stock.domain.Stock;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 종목 마스터 응답. (CLAUDE.md §24 – GET /api/market/stocks)
 *
 * <p>전일 종가는 여기 없다. 종목 마스터가 아니라 <b>시세</b>에 속한 값이고,
 * 설정의 {@code previous-close}는 Mock 모드의 출발 가격일 뿐이다.
 * kis 모드에서 그 값을 내보내면 실제와 전혀 다른 숫자가 나간다.
 * 전일 종가는 {@code GET /api/market/stocks/{symbol}/price} 가 준다.
 */
public record StockResponse(
        @Schema(description = "종목코드 (6자리)", example = "005930") String symbol,
        @Schema(description = "종목명", example = "삼성전자") String name,
        @Schema(description = "시장 구분", example = "KOSPI") String market) {

    public static StockResponse from(Stock stock) {
        return new StockResponse(stock.symbol(), stock.name(), stock.market());
    }
}
