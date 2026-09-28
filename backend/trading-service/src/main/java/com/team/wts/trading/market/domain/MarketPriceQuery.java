package com.team.wts.trading.market.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 최신 시세 조회 포트. (CLAUDE.md §6.2, §7)
 *
 * <p>Trading Service는 Market Service를 HTTP로 호출하지 않는다.
 * 구현체는 Valkey를 읽는다 (ADR-0008).
 */
public interface MarketPriceQuery {

    Optional<MarketPrice> findLatest(String symbol);

    /**
     * 여러 종목을 한 번에 읽는다. 시세가 없는 종목은 결과에서 빠진다.
     *
     * <p>포트폴리오는 보유 종목 수만큼 조회가 필요하다. 한 번의 왕복으로 끝낸다.
     */
    Map<String, MarketPrice> findLatest(Collection<String> symbols);
}
