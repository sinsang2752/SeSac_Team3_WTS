package com.team.wts.trading.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시세 신선도 기준. (CLAUDE.md §11)
 *
 * @param maxAge 이 시간을 넘긴 현재가로는 시장가 주문을 체결하지 않는다.
 */
@ConfigurationProperties(prefix = "market.price")
public record MarketPriceProperties(Duration maxAge) {

    public MarketPriceProperties {
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("market.price.max-age 는 0보다 커야 한다: " + maxAge);
        }
    }
}
