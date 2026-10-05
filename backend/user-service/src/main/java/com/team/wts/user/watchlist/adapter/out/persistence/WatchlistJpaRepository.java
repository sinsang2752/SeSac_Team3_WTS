package com.team.wts.user.watchlist.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.wts.user.watchlist.domain.WatchlistItem;
import com.team.wts.user.watchlist.domain.WatchlistRepository;

public interface WatchlistJpaRepository
        extends WatchlistRepository, JpaRepository<WatchlistItem, Long> {
}
