package com.team.wts.market.stock.adapter.in.web.dto;

import java.math.BigDecimal;

import com.team.wts.market.stock.domain.Market;
import com.team.wts.market.stock.domain.Stock;
import com.team.wts.market.support.Decimals;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 종목 마스터 응답. (CLAUDE.md §24, §57.1)
 *
 * <p>전일 종가는 여기 없다. 종목 마스터가 아니라 <b>시세</b>에 속한 값이다.
 * 기준가(basePrice)는 가격제한폭의 기준이고, 대개 전일 종가와 같지만 권리락 등으로 다를 수 있다.
 * 전일 종가는 {@code GET /api/market/stocks/{symbol}/price} 가 준다.
 */
public record StockResponse(
        @Schema(description = "종목코드 (영문 대문자·숫자 6자리)", example = "005930") String symbol,
        @Schema(description = "표준코드 (ISIN)", example = "KR7005930003") String standardCode,
        @Schema(description = "종목명", example = "삼성전자") String name,
        @Schema(description = "시장 구분", example = "KOSPI") Market market,
        @Schema(description = "기준가 (원). 가격제한폭의 기준이다. 신규 상장 직후처럼 모르면 null",
                example = "276000") BigDecimal basePrice,
        @Schema(description = "주문을 받을 수 있는가. 거래정지 · 상장폐지면 false", example = "true")
        boolean tradable) {

    public static StockResponse from(Stock stock) {
        return new StockResponse(stock.symbol(), stock.standardCode(), stock.name(), stock.market(),
                Decimals.plain(stock.basePrice()), stock.tradable());
    }
}
