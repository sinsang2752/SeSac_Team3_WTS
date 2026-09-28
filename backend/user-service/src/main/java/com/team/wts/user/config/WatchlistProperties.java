package com.team.wts.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관심종목 설정.
 *
 * @param maxSize 사용자당 최대 관심종목 수. 한도를 코드에 박지 않는다 (CLAUDE.md §20의 취지).
 */
@ConfigurationProperties(prefix = "user.watchlist")
public record WatchlistProperties(int maxSize) {

    public WatchlistProperties {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("user.watchlist.max-size 는 1 이상이어야 한다: " + maxSize);
        }
    }
}
