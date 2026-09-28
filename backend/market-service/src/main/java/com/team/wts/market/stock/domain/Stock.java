package com.team.wts.market.stock.domain;

import java.math.BigDecimal;

/**
 * 종목 기본정보. (CLAUDE.md §24 – GET /api/market/stocks)
 *
 * <p>MVP에서는 설정값에서 읽는다. 실제 종목 마스터 연동은 Phase 6(KIS)에서 다룬다.
 *
 * @param previousClose 전일 종가. 등락 계산의 기준값이다.
 */
public record Stock(
        String symbol,
        String name,
        String market,
        BigDecimal previousClose) {
}
