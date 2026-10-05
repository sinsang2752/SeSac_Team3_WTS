package com.team.wts.trading.market.adapter.out.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.wts.trading.market.domain.MarketPrice;
import com.team.wts.trading.market.domain.MarketPriceQuery;

/**
 * market-service가 써 둔 최신 시세를 읽는다. (CLAUDE.md §18 – {@code market:price:005930})
 *
 * <p>키 형식이 두 서비스 사이의 계약이다. 바꾸려면 양쪽을 함께 고쳐야 한다.
 *
 * <p>읽기 실패는 {@link Optional#empty()}로 돌려준다. 호출자는 이를 "시세 없음"으로 보고
 * 주문을 거절한다. 시세를 모르는 채로 체결하는 것보다 거절이 안전하다.
 */
@Component
public class ValkeyMarketPriceQuery implements MarketPriceQuery {

    private static final Logger log = LoggerFactory.getLogger(ValkeyMarketPriceQuery.class);

    /** market-service {@code ValkeyQuoteCache}와 맞춘 키 접두사. */
    private static final String PRICE_KEY_PREFIX = "market:price:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ValkeyMarketPriceQuery(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<MarketPrice> findLatest(String symbol) {
        try {
            return parse(symbol, redis.opsForValue().get(PRICE_KEY_PREFIX + symbol));
        } catch (RedisConnectionFailureException e) {
            log.warn("Valkey 읽기 실패: symbol={} ({})", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Map<String, MarketPrice> findLatest(Collection<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        // 순서를 유지해야 결과를 종목과 짝지을 수 있다.
        List<String> ordered = new ArrayList<>(symbols);
        List<String> keys = ordered.stream().map(symbol -> PRICE_KEY_PREFIX + symbol).toList();

        List<String> values;
        try {
            values = redis.opsForValue().multiGet(keys);
        } catch (RedisConnectionFailureException e) {
            log.warn("Valkey 일괄 읽기 실패: symbols={} ({})", ordered, e.getMessage());
            return Map.of();
        }
        if (values == null) {
            return Map.of();
        }

        Map<String, MarketPrice> prices = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size() && i < values.size(); i++) {
            String symbol = ordered.get(i);
            parse(symbol, values.get(i)).ifPresent(price -> prices.put(symbol, price));
        }
        return prices;
    }

    private Optional<MarketPrice> parse(String symbol, String json) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, MarketPrice.class));
        } catch (JsonProcessingException e) {
            // 형식이 바뀐 낡은 값이 남아 있는 경우다. 다음 틱이 덮어쓴다.
            log.error("시세 역직렬화 실패: symbol={}", symbol, e);
            return Optional.empty();
        }
    }
}
