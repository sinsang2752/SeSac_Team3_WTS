package com.team.wts.user.watchlist.adapter.in.web.dto;

import java.time.Instant;

import com.team.wts.user.watchlist.domain.WatchlistItem;

/** 관심종목 응답. (CLAUDE.md §24) */
public record WatchlistItemResponse(String symbol, Instant createdAt) {

    public static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(item.symbol(), item.createdAt());
    }
}
