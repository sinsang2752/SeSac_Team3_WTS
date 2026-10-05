package com.team.wts.user.watchlist.application.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.common.market.StockSymbol;
import com.team.wts.user.config.WatchlistProperties;
import com.team.wts.user.watchlist.domain.WatchlistItem;
import com.team.wts.user.watchlist.domain.WatchlistRepository;

/** 관심종목 추가·삭제·조회. (CLAUDE.md §24) */
@Service
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);

    private final WatchlistRepository watchlists;
    private final WatchlistProperties properties;

    public WatchlistService(WatchlistRepository watchlists, WatchlistProperties properties) {
        this.watchlists = watchlists;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItem> findAll(String userId) {
        return watchlists.findByUserIdOrderByIdAsc(userId);
    }

    /**
     * 관심종목에 담는다.
     *
     * <p>이미 담긴 종목을 다시 담아도 오류가 아니다. 사용자가 원한 상태(담겨 있음)가 이미
     * 만족됐기 때문이다. 기존 항목을 그대로 돌려준다.
     */
    @Transactional
    public WatchlistItem add(String userId, String symbol) {
        if (!WatchlistItem.isValidSymbol(symbol)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, StockSymbol.FORMAT_MESSAGE);
        }
        return watchlists.findByUserIdAndSymbol(userId, symbol).orElseGet(() -> create(userId, symbol));
    }

    private WatchlistItem create(String userId, String symbol) {
        long count = watchlists.countByUserId(userId);
        if (count >= properties.maxSize()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "관심종목은 최대 " + properties.maxSize() + "개까지 담을 수 있습니다.");
        }
        WatchlistItem saved = watchlists.save(WatchlistItem.of(userId, symbol));
        log.info("관심종목 추가: userId={} symbol={}", userId, symbol);
        return saved;
    }

    /** 담겨 있지 않은 종목을 빼도 오류가 아니다. 결과 상태가 같다. */
    @Transactional
    public void remove(String userId, String symbol) {
        watchlists.findByUserIdAndSymbol(userId, symbol).ifPresent(item -> {
            watchlists.delete(item);
            log.info("관심종목 삭제: userId={} symbol={}", userId, symbol);
        });
    }
}
