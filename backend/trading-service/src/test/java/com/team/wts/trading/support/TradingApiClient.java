package com.team.wts.trading.support;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.team.wts.common.web.WtsHeaders;

/** 통합 테스트에서 반복되는 HTTP 호출. */
@Component
public class TradingApiClient {

    private final TestRestTemplate rest;

    public TradingApiClient(TestRestTemplate rest) {
        this.rest = rest;
    }

    public ResponseEntity<Map<String, Object>> place(String userId, Map<String, Object> body) {
        return place(userId, UUID.randomUUID().toString(), body);
    }

    public ResponseEntity<Map<String, Object>> place(String userId, String idempotencyKey,
            Map<String, Object> body) {
        HttpHeaders headers = headers(userId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WtsHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        return rest.exchange("/api/trading/orders", HttpMethod.POST, new HttpEntity<>(body, headers),
                map());
    }

    public ResponseEntity<Map<String, Object>> cancel(String userId, long orderId) {
        return rest.exchange("/api/trading/orders/" + orderId, HttpMethod.DELETE,
                new HttpEntity<>(headers(userId)), map());
    }

    /** 상태 코드만 볼 때. 본문이 배열인지 객체인지 상관하지 않는다. */
    public ResponseEntity<String> rawGet(String userId, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(userId)), String.class);
    }

    public Map<String, Object> get(String userId, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(userId)), map())
                .getBody();
    }

    public List<Map<String, Object>> list(String userId, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(userId)), list())
                .getBody();
    }

    public Map<String, Object> account(String userId) {
        return get(userId, "/api/trading/account");
    }

    public BigDecimal cashBalance(String userId) {
        return decimal(account(userId), "cashBalance");
    }

    public BigDecimal reservedCash(String userId) {
        return decimal(account(userId), "reservedCash");
    }

    public static BigDecimal decimal(Map<String, Object> body, String field) {
        return new BigDecimal(String.valueOf(body.get(field)));
    }

    public static Map<String, Object> order(String symbol, String side, String orderType,
            long quantity, String limitPrice) {
        Map<String, Object> body = new HashMap<>();
        body.put("symbol", symbol);
        body.put("side", side);
        body.put("orderType", orderType);
        body.put("quantity", quantity);
        body.put("limitPrice", limitPrice == null ? null : new BigDecimal(limitPrice));
        return body;
    }

    private static HttpHeaders headers(String userId) {
        HttpHeaders headers = new HttpHeaders();
        // Gateway가 토큰을 검증한 뒤 심어주는 헤더다 (CLAUDE.md §6.1).
        headers.set(WtsHeaders.USER_ID, userId);
        return headers;
    }

    private static ParameterizedTypeReference<Map<String, Object>> map() {
        return new ParameterizedTypeReference<>() {
        };
    }

    private static ParameterizedTypeReference<List<Map<String, Object>>> list() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
