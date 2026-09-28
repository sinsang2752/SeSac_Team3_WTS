package com.team.wts.market.quote.adapter.ws.dto;

import java.util.List;

/**
 * 클라이언트가 보내는 메시지. (CLAUDE.md §25)
 *
 * <pre>
 * { "type": "SUBSCRIBE",   "symbols": ["005930", "000660"] }
 * { "type": "UNSUBSCRIBE", "symbols": ["005930"] }
 * </pre>
 */
public record ClientCommand(String type, List<String> symbols) {

    public static final String SUBSCRIBE = "SUBSCRIBE";
    public static final String UNSUBSCRIBE = "UNSUBSCRIBE";

    public ClientCommand {
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
    }
}
