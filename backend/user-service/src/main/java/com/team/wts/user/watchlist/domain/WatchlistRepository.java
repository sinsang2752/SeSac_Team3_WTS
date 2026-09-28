package com.team.wts.user.watchlist.domain;

import java.util.List;
import java.util.Optional;

/** 관심종목 영속화 포트. (CLAUDE.md §7) */
public interface WatchlistRepository {

    WatchlistItem save(WatchlistItem item);

    List<WatchlistItem> findByUserIdOrderByIdAsc(String userId);

    Optional<WatchlistItem> findByUserIdAndSymbol(String userId, String symbol);

    long countByUserId(String userId);

    void delete(WatchlistItem item);
}
