package com.team.wts.trading.position.domain;

import java.util.List;
import java.util.Optional;

/** 포지션 영속화 포트. (CLAUDE.md §7) */
public interface PositionRepository {

    Position save(Position position);

    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);

    /** 보유 중인 종목만. 전량 매도한 행(quantity = 0)은 내역에 노출하지 않는다. */
    List<Position> findByAccountIdAndQuantityGreaterThanOrderBySymbolAsc(Long accountId, long quantity);
}
