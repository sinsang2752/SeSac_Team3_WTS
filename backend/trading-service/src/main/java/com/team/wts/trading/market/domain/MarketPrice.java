package com.team.wts.trading.market.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 체결 판정에 쓰는 최신 현재가. (CLAUDE.md §11, §12)
 *
 * <p>market-service가 Valkey에 써 둔 값을 읽어 만든다. 같은 이름의 클래스가 market-service에도
 * 있지만 일부러 공유하지 않는다. 공유하면 시세 모델을 바꿀 때마다 거래 서비스가 함께 흔들린다.
 * 여기서는 <b>체결에 필요한 필드만</b> 받는다. 나머지(previousClose, volume)는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketPrice(String symbol, BigDecimal price, Instant timestamp) {

    /**
     * 시세가 너무 오래됐는지 판단한다. (CLAUDE.md §11)
     *
     * <p>market-service가 죽어 있으면 Valkey의 마지막 값이 그대로 남는다.
     * TTL로 지우지 않고 timestamp로 판단하는 이유다.
     */
    public boolean isStale(Duration maxAge, Instant now) {
        return timestamp.plus(maxAge).isBefore(now);
    }
}
