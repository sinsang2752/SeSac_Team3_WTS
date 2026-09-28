package com.team.wts.market.quote.domain;

/**
 * 구독 중인 WTS 클라이언트로 실시간 송신하는 포트. (CLAUDE.md §6.2, §25)
 *
 * <p>MVP는 단일 인스턴스이므로 수신한 서버가 자기 세션에 바로 내보낸다.
 * 인스턴스가 여러 개가 되면 Valkey pub/sub로 fan-out을 보조해야 한다 (§18).
 */
public interface QuoteBroadcaster {

    void broadcastPrice(MarketPrice price);

    void broadcastOrderBook(OrderBook orderBook);
}
