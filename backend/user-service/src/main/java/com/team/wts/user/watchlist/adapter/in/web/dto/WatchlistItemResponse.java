package com.team.wts.user.watchlist.adapter.in.web.dto;

import java.time.Instant;

import com.team.wts.user.watchlist.domain.WatchlistItem;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관심종목 응답. (CLAUDE.md §24) */
public record WatchlistItemResponse(
        @Schema(description = "종목코드", example = "005930") String symbol,
        @Schema(description = "담은 시각 (UTC)", example = "2026-09-22T08:12:44.101Z") Instant createdAt) {

    public static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(item.symbol(), item.createdAt());
    }
}
