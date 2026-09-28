package com.team.wts.user.watchlist.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.user.watchlist.adapter.in.web.dto.WatchlistItemResponse;
import com.team.wts.user.watchlist.application.service.WatchlistService;

/** 관심종목 API. (CLAUDE.md §24) */
@RestController
@RequestMapping("/api/users/me/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItemResponse> list(@CurrentUserId String userId) {
        return watchlistService.findAll(userId).stream()
                .map(WatchlistItemResponse::from)
                .toList();
    }

    /** 이미 담긴 종목이어도 성공이다. 같은 요청을 여러 번 보내도 결과가 같다. */
    @PostMapping("/{symbol}")
    public WatchlistItemResponse add(@CurrentUserId String userId, @PathVariable String symbol) {
        return WatchlistItemResponse.from(watchlistService.add(userId, symbol));
    }

    @DeleteMapping("/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentUserId String userId, @PathVariable String symbol) {
        watchlistService.remove(userId, symbol);
    }
}
