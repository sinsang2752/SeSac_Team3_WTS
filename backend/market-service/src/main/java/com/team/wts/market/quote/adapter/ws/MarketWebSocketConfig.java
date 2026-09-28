package com.team.wts.market.quote.adapter.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 엔드포인트 등록. (CLAUDE.md §25)
 *
 * <p>Origin 제한을 두지 않는 이유: 이 포트는 외부에 노출하지 않고 Gateway를 통해서만 접근한다.
 * CORS/Origin 정책은 Gateway가 담당한다 (§6.1).
 */
@Configuration
@EnableWebSocket
public class MarketWebSocketConfig implements WebSocketConfigurer {

    private final MarketWebSocketHandler handler;

    public MarketWebSocketConfig(MarketWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/market").setAllowedOriginPatterns("*");
    }
}
