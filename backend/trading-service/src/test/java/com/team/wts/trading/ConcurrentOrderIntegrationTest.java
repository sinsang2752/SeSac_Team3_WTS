package com.team.wts.trading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.team.wts.common.web.WtsHeaders;
import com.team.wts.trading.support.IntegrationTestBase;
import com.team.wts.trading.support.MarketPriceFixture;

/**
 * 동시 주문에서도 계좌 정합성이 깨지지 않는지 본다. (CLAUDE.md §2, §13, ADR-0009)
 *
 * <p>"예수금은 음수가 될 수 없다"와 "동일 주문 요청이 중복 생성되지 않는다"는 MVP 성공 기준이다.
 * 단일 요청 테스트로는 이 두 가지를 증명할 수 없다. 계좌 행의 비관적 락이 실제로
 * 한 사용자의 주문을 직렬화하는지 확인한다.
 */
class ConcurrentOrderIntegrationTest extends IntegrationTestBase {

    private static final String SAMSUNG = "005930";
    private static final int THREADS = 6;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MarketPriceFixture marketPrice;

    @Test
    @DisplayName("예수금으로 한 건만 감당되는 주문을 동시에 내면 한 건만 체결된다")
    void concurrentBuysCannotOverdrawCash() throws Exception {
        String user = openedAccount();
        marketPrice.publish(SAMSUNG, "80000");

        // 80,000 × 1,000 = 8천만원. 1억원으로는 한 건만 가능하다.
        List<ResponseEntity<Map<String, Object>>> responses = inParallel(
                () -> place(user, UUID.randomUUID().toString(), marketBuy(1000)));

        long filled = responses.stream()
                .filter(r -> "FILLED".equals(value(r, "status")))
                .count();
        long rejected = responses.stream()
                .filter(r -> "INSUFFICIENT_BALANCE".equals(value(r, "code")))
                .count();

        assertThat(filled).isEqualTo(1);
        assertThat(rejected).isEqualTo(THREADS - 1);
        assertThat(cashBalance(user)).isEqualByComparingTo("20000000");
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 동시에 보내도 주문은 하나만 생긴다 (CLAUDE.md §14)")
    void concurrentRequestsWithSameKeyCreateOneOrder() throws Exception {
        String user = openedAccount();
        String key = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");

        List<ResponseEntity<Map<String, Object>>> responses = inParallel(
                () -> place(user, key, marketBuy(10)));

        List<Object> orderIds = responses.stream()
                .map(r -> value(r, "orderId"))
                .distinct()
                .toList();

        assertThat(orderIds).hasSize(1);
        // 6번 요청해도 체결은 한 번이다.
        assertThat(cashBalance(user)).isEqualByComparingTo("99200000");
        assertThat(orders(user)).hasSize(1);
    }

    /** 모든 스레드가 같은 순간에 출발하도록 배리어로 맞춘다. */
    private List<ResponseEntity<Map<String, Object>>> inParallel(
            Callable<ResponseEntity<Map<String, Object>>> task) throws Exception {
        CyclicBarrier start = new CyclicBarrier(THREADS);
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<ResponseEntity<Map<String, Object>>>> futures =
                    pool.invokeAll(java.util.Collections.nCopies(THREADS, (Callable<ResponseEntity<Map<String, Object>>>) () -> {
                        start.await(10, TimeUnit.SECONDS);
                        return task.call();
                    }));
            List<ResponseEntity<Map<String, Object>>> responses = new java.util.ArrayList<>();
            for (Future<ResponseEntity<Map<String, Object>>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        }
    }

    /** 계좌를 미리 열어 둔다. 계좌 개설 자체의 경합은 이 테스트의 관심사가 아니다. */
    private String openedAccount() {
        String userId = UUID.randomUUID().toString();
        account(userId);
        return userId;
    }

    private static Object value(ResponseEntity<Map<String, Object>> response, String key) {
        return response.getBody() == null ? null : response.getBody().get(key);
    }

    private static Map<String, Object> marketBuy(long quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("symbol", SAMSUNG);
        body.put("side", "BUY");
        body.put("orderType", "MARKET");
        body.put("quantity", quantity);
        return body;
    }

    private ResponseEntity<Map<String, Object>> place(String userId, String idempotencyKey,
            Map<String, Object> body) {
        HttpHeaders headers = userHeaders(userId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WtsHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        return rest.exchange("/api/trading/orders", HttpMethod.POST,
                new HttpEntity<>(body, headers), mapType());
    }

    private List<Map<String, Object>> orders(String userId) {
        return rest.exchange("/api/trading/orders", HttpMethod.GET,
                new HttpEntity<>(userHeaders(userId)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    private Map<String, Object> account(String userId) {
        return rest.exchange("/api/trading/account", HttpMethod.GET,
                new HttpEntity<>(userHeaders(userId)), mapType()).getBody();
    }

    private BigDecimal cashBalance(String userId) {
        return new BigDecimal(account(userId).get("cashBalance").toString());
    }

    private static HttpHeaders userHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(WtsHeaders.USER_ID, userId);
        return headers;
    }

    private static ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
