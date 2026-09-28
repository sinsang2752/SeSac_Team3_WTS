package com.team.wts.trading.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** 시세 신선도와 market-service가 써 둔 JSON 형식. (CLAUDE.md §11, §18) */
class MarketPriceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T08:50:10Z");

    @Test
    @DisplayName("max-age를 넘긴 시세는 stale이다")
    void staleWhenOlderThanMaxAge() {
        MarketPrice price = new MarketPrice("005930", new BigDecimal("79800"),
                Instant.parse("2026-09-21T08:50:04Z"));

        assertThat(price.isStale(Duration.ofSeconds(5), NOW)).isTrue();
    }

    @Test
    @DisplayName("max-age 경계값은 아직 stale이 아니다")
    void notStaleExactlyAtMaxAge() {
        MarketPrice price = new MarketPrice("005930", new BigDecimal("79800"),
                Instant.parse("2026-09-21T08:50:05Z"));

        assertThat(price.isStale(Duration.ofSeconds(5), NOW)).isFalse();
    }

    @Test
    @DisplayName("market-service가 Valkey에 써 둔 JSON을 그대로 읽는다")
    void readsMarketServiceCacheFormat() throws Exception {
        // market-service ValkeyQuoteCache가 만드는 실제 형식.
        // previousClose와 volume은 체결 판정에 쓰지 않으므로 무시한다.
        String json = """
                {"symbol":"005930","price":85400,"previousClose":80000,
                 "volume":31450149,"timestamp":"2026-09-22T01:12:10.766711Z"}
                """;
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

        MarketPrice price = mapper.readValue(json, MarketPrice.class);

        assertThat(price.symbol()).isEqualTo("005930");
        assertThat(price.price()).isEqualByComparingTo("85400");
        assertThat(price.timestamp()).isEqualTo(Instant.parse("2026-09-22T01:12:10.766711Z"));
    }
}
