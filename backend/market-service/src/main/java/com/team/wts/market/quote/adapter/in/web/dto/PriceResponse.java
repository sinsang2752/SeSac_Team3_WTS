package com.team.wts.market.quote.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.team.wts.market.quote.domain.MarketPrice;

/**
 * 현재가 응답. WebSocket PRICE 메시지와 같은 필드 구성이다 (CLAUDE.md §25).
 *
 * <p>timestamp는 UTC다. 프론트엔드가 Asia/Seoul로 표시한다 (§43).
 */
public record PriceResponse(
        String symbol,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changeRate,
        long volume,
        Instant timestamp) {

    public static PriceResponse from(MarketPrice source) {
        return new PriceResponse(source.symbol(), source.price(), source.previousClose(),
                source.change(), source.changeRate(), source.volume(), source.timestamp());
    }
}
