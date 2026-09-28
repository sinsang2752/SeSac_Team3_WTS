package com.team.wts.trading.support;

import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * market-service 역할을 대신해 Valkey에 최신 시세를 심는다.
 *
 * <p>JSON을 손으로 쓴다. 두 서비스가 Valkey 키와 값 형식으로만 연결돼 있다는 사실을
 * 테스트에서도 그대로 유지하기 위해서다. 형식이 어긋나면 이 테스트가 먼저 깨져야 한다.
 */
@Component
public class MarketPriceFixture {

    private static final String PRICE_KEY_PREFIX = "market:price:";

    private final StringRedisTemplate redis;

    public MarketPriceFixture(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 방금 생성된 시세. */
    public void publish(String symbol, String price) {
        publishAt(symbol, price, Instant.now());
    }

    public void publishAt(String symbol, String price, Instant timestamp) {
        String json = """
                {"symbol":"%s","price":%s,"previousClose":80000,"volume":1234,"timestamp":"%s"}"""
                .formatted(symbol, price, timestamp);
        redis.opsForValue().set(PRICE_KEY_PREFIX + symbol, json);
    }

    public void clear(String symbol) {
        redis.delete(PRICE_KEY_PREFIX + symbol);
    }
}
