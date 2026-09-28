package com.team.wts.trading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.team.wts.common.web.WtsHeaders;
import com.team.wts.trading.support.IntegrationTestBase;
import com.team.wts.trading.support.MarketPriceFixture;

/**
 * Phase 3 완료조건: 시장가 매수 후 cash가 줄고 position이 늘어난다. (CLAUDE.md §47)
 *
 * <p>CLAUDE.md §35가 반드시 테스트하라고 정한 거래 시나리오를 함께 검증한다.
 * 실현손익(Ledger)은 Phase 4다.
 */
class OrderApiIntegrationTest extends IntegrationTestBase {

    private static final String SAMSUNG = "005930";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MarketPriceFixture marketPrice;

    // ── 완료조건 ──────────────────────────────────────────────

    @Test
    @DisplayName("시장가 매수가 즉시 체결되고 예수금이 줄고 포지션이 는다")
    void marketBuyFillsImmediatelyAndMovesCashAndPosition() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");

        ResponseEntity<Map<String, Object>> placed = place(user, marketBuy(SAMSUNG, 10));

        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(placed.getBody()).containsEntry("status", "FILLED");
        assertThat(placed.getBody()).containsEntry("filledQuantity", 10);

        // 100,000,000 - (80,000 × 10) = 99,200,000
        assertThat(cashBalance(user)).isEqualByComparingTo("99200000");
        assertThat(reservedCash(user)).isEqualByComparingTo("0");

        Map<String, Object> position = onlyPosition(user);
        assertThat(position).containsEntry("symbol", SAMSUNG);
        assertThat(position).containsEntry("quantity", 10);
        assertThat(new BigDecimal(position.get("averagePrice").toString()))
                .isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("두 번 매수하면 평균단가가 이동한다 (CLAUDE.md §35)")
    void averagePriceMovesAcrossBuys() {
        String user = newUser();

        marketPrice.publish(SAMSUNG, "80000");
        place(user, marketBuy(SAMSUNG, 10));
        marketPrice.publish(SAMSUNG, "90000");
        place(user, marketBuy(SAMSUNG, 10));

        Map<String, Object> position = onlyPosition(user);
        assertThat(position).containsEntry("quantity", 20);
        assertThat(new BigDecimal(position.get("averagePrice").toString()))
                .isEqualByComparingTo("85000");
        assertThat(cashBalance(user)).isEqualByComparingTo("98300000");
    }

    @Test
    @DisplayName("매도가 체결되면 예수금이 늘고 보유수량이 준다")
    void sellFillIncreasesCashAndReducesPosition() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        place(user, marketBuy(SAMSUNG, 10));

        marketPrice.publish(SAMSUNG, "85000");
        ResponseEntity<Map<String, Object>> sold = place(user, marketSell(SAMSUNG, 4));

        assertThat(sold.getBody()).containsEntry("status", "FILLED");
        // 99,200,000 + (85,000 × 4) = 99,540,000
        assertThat(cashBalance(user)).isEqualByComparingTo("99540000");

        Map<String, Object> position = onlyPosition(user);
        assertThat(position).containsEntry("quantity", 6);
        // 매도는 평균단가를 바꾸지 않는다.
        assertThat(new BigDecimal(position.get("averagePrice").toString()))
                .isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("전량 매도하면 포지션 목록에서 빠진다")
    void fullySoldPositionDisappearsFromList() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        place(user, marketBuy(SAMSUNG, 10));
        place(user, marketSell(SAMSUNG, 10));

        assertThat(positions(user)).isEmpty();
    }

    // ── 주문 검증 (CLAUDE.md §35) ─────────────────────────────

    @Test
    @DisplayName("잔고 부족 매수는 거절된다")
    void rejectsBuyBeyondAvailableCash() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");

        // 1억원으로 80,000원짜리를 2,000주(=1.6억) 살 수 없다.
        ResponseEntity<Map<String, Object>> response = place(user, marketBuy(SAMSUNG, 2000));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "INSUFFICIENT_BALANCE");
        assertThat(cashBalance(user)).isEqualByComparingTo(DEFAULT_INITIAL_CASH);

        // 거절 주문도 내역에 남는다.
        Map<String, Object> rejected = orders(user).get(0);
        assertThat(rejected).containsEntry("status", "REJECTED");
        assertThat(rejected).containsEntry("rejectReason", "INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("보유수량 초과 매도는 거절된다")
    void rejectsSellBeyondAvailableQuantity() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        place(user, marketBuy(SAMSUNG, 10));

        ResponseEntity<Map<String, Object>> response = place(user, marketSell(SAMSUNG, 11));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "INSUFFICIENT_POSITION");
        assertThat(onlyPosition(user)).containsEntry("quantity", 10);
    }

    @Test
    @DisplayName("한 번도 보유한 적 없는 종목은 매도할 수 없다")
    void rejectsSellWithoutPosition() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");

        ResponseEntity<Map<String, Object>> response = place(user, marketSell(SAMSUNG, 1));

        assertThat(response.getBody()).containsEntry("code", "INSUFFICIENT_POSITION");
        assertThat(positions(user)).isEmpty();
    }

    @Test
    @DisplayName("시세가 stale이면 시장가 주문을 거절한다 (CLAUDE.md §11)")
    void rejectsMarketOrderOnStalePrice() {
        String user = newUser();
        marketPrice.publishAt(SAMSUNG, "80000", java.time.Instant.now().minusSeconds(600));

        ResponseEntity<Map<String, Object>> response = place(user, marketBuy(SAMSUNG, 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("code", "MARKET_PRICE_STALE");
    }

    @Test
    @DisplayName("시세가 없는 종목은 주문할 수 없다")
    void rejectsOrderWithoutQuote() {
        String user = newUser();
        marketPrice.clear("999999");

        ResponseEntity<Map<String, Object>> response = place(user, marketBuy("999999", 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("code", "MARKET_PRICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("종목코드 형식이 틀리면 400이다")
    void rejectsMalformedSymbol() {
        ResponseEntity<Map<String, Object>> response = place(newUser(), marketBuy("SAMSUNG", 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
    }

    // ── 지정가 (CLAUDE.md §12) ────────────────────────────────

    @Test
    @DisplayName("가격 조건을 만족하지 않는 지정가 주문은 ACCEPTED로 남고 예수금을 묶는다")
    void limitOrderRestsWhenPriceConditionUnmet() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");

        ResponseEntity<Map<String, Object>> response = place(user, limitBuy(SAMSUNG, 10, "70000"));

        assertThat(response.getBody()).containsEntry("status", "ACCEPTED");
        assertThat(response.getBody()).containsEntry("filledQuantity", 0);
        // 예수금은 그대로, 주문 가능 금액만 줄어든다 (CLAUDE.md §9.2).
        assertThat(cashBalance(user)).isEqualByComparingTo(DEFAULT_INITIAL_CASH);
        assertThat(reservedCash(user)).isEqualByComparingTo("700000");
        assertThat(availableCash(user)).isEqualByComparingTo("99300000");
        assertThat(positions(user)).isEmpty();
    }

    @Test
    @DisplayName("가격 조건을 만족하는 지정가 매수는 현재가로 체결된다")
    void limitBuyFillsAtCurrentPriceWhenConditionMet() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "79000");

        ResponseEntity<Map<String, Object>> response = place(user, limitBuy(SAMSUNG, 10, "80000"));

        assertThat(response.getBody()).containsEntry("status", "FILLED");
        // 지정가 80,000이 아니라 현재가 79,000으로 체결된다. 차액은 계좌에 남는다.
        assertThat(cashBalance(user)).isEqualByComparingTo("99210000");
        assertThat(reservedCash(user)).isEqualByComparingTo("0");
        assertThat(new BigDecimal(onlyPosition(user).get("averagePrice").toString()))
                .isEqualByComparingTo("79000");
    }

    @Test
    @DisplayName("미체결 지정가 매도는 보유수량을 묶는다")
    void limitSellReservesQuantity() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        place(user, marketBuy(SAMSUNG, 10));

        place(user, limitSell(SAMSUNG, 4, "99000"));

        Map<String, Object> position = onlyPosition(user);
        assertThat(position).containsEntry("quantity", 10);
        assertThat(position).containsEntry("reservedQuantity", 4);
        assertThat(position).containsEntry("availableQuantity", 6);

        // 묶인 4주는 다시 팔 수 없다.
        assertThat(place(user, marketSell(SAMSUNG, 7)).getBody())
                .containsEntry("code", "INSUFFICIENT_POSITION");
    }

    // ── 취소 (CLAUDE.md §35) ──────────────────────────────────

    @Test
    @DisplayName("미체결 주문을 취소하면 묶인 예수금이 풀린다")
    void cancellingOpenOrderReleasesReservation() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        Integer orderId = (Integer) place(user, limitBuy(SAMSUNG, 10, "70000")).getBody().get("orderId");

        ResponseEntity<Map<String, Object>> cancelled = cancel(user, orderId);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody()).containsEntry("status", "CANCELLED");
        assertThat(reservedCash(user)).isEqualByComparingTo("0");
        assertThat(availableCash(user)).isEqualByComparingTo(DEFAULT_INITIAL_CASH);
    }

    @Test
    @DisplayName("미체결 매도 주문을 취소하면 묶인 수량이 풀린다")
    void cancellingOpenSellReleasesQuantity() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        place(user, marketBuy(SAMSUNG, 10));
        Integer orderId = (Integer) place(user, limitSell(SAMSUNG, 4, "99000")).getBody().get("orderId");

        cancel(user, orderId);

        assertThat(onlyPosition(user)).containsEntry("availableQuantity", 10);
    }

    @Test
    @DisplayName("이미 체결된 주문은 취소할 수 없다")
    void cannotCancelFilledOrder() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        Integer orderId = (Integer) place(user, marketBuy(SAMSUNG, 1)).getBody().get("orderId");

        ResponseEntity<Map<String, Object>> response = cancel(user, orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "INVALID_ORDER_STATE");
    }

    @Test
    @DisplayName("남의 주문은 존재하지 않는 것으로 취급한다")
    void cannotTouchAnotherUsersOrder() {
        String owner = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        Integer orderId = (Integer) place(owner, limitBuy(SAMSUNG, 1, "70000")).getBody().get("orderId");

        ResponseEntity<Map<String, Object>> response = cancel(newUser(), orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "ORDER_NOT_FOUND");
    }

    // ── Idempotency (CLAUDE.md §14, §35) ──────────────────────

    @Test
    @DisplayName("같은 Idempotency-Key로 재요청하면 주문이 하나만 생긴다")
    void sameIdempotencyKeyDoesNotCreateSecondOrder() {
        String user = newUser();
        String key = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");

        ResponseEntity<Map<String, Object>> first = place(user, key, marketBuy(SAMSUNG, 10));
        ResponseEntity<Map<String, Object>> second = place(user, key, marketBuy(SAMSUNG, 10));

        assertThat(second.getBody().get("orderId")).isEqualTo(first.getBody().get("orderId"));
        assertThat(orders(user)).hasSize(1);
        // 재요청이 예수금을 두 번 깎지 않는다.
        assertThat(cashBalance(user)).isEqualByComparingTo("99200000");
        assertThat(onlyPosition(user)).containsEntry("quantity", 10);
    }

    @Test
    @DisplayName("같은 키로 다른 내용을 요청하면 409다")
    void sameKeyWithDifferentPayloadConflicts() {
        String user = newUser();
        String key = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");

        place(user, key, marketBuy(SAMSUNG, 10));
        ResponseEntity<Map<String, Object>> second = place(user, key, marketBuy(SAMSUNG, 20));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).containsEntry("code", "DUPLICATE_ORDER_REQUEST");
        assertThat(orders(user)).hasSize(1);
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400이다")
    void requiresIdempotencyKeyHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WtsHeaders.USER_ID, newUser());

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/trading/orders", HttpMethod.POST,
                new HttpEntity<>(marketBuy(SAMSUNG, 1), headers), mapType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
    }

    // ── 조회 ──────────────────────────────────────────────────

    @Test
    @DisplayName("status로 미체결 주문만 걸러낼 수 있다")
    void filtersOpenOrdersByStatus() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        place(user, marketBuy(SAMSUNG, 1));
        place(user, limitBuy(SAMSUNG, 1, "70000"));

        List<Map<String, Object>> open = rest.exchange(
                "/api/trading/orders?status=ACCEPTED", HttpMethod.GET,
                new HttpEntity<>(userHeaders(user)), listType()).getBody();

        assertThat(open).hasSize(1);
        assertThat(open.get(0)).containsEntry("orderType", "LIMIT");
    }

    @Test
    @DisplayName("단건 조회는 주문 상세를 돌려준다")
    void findsSingleOrder() {
        String user = newUser();
        marketPrice.publish(SAMSUNG, "80000");
        Integer orderId = (Integer) place(user, limitBuy(SAMSUNG, 3, "70000")).getBody().get("orderId");

        Map<String, Object> order = rest.exchange(
                "/api/trading/orders/" + orderId, HttpMethod.GET,
                new HttpEntity<>(userHeaders(user)), mapType()).getBody();

        assertThat(order).containsEntry("symbol", SAMSUNG);
        assertThat(order).containsEntry("quantity", 3);
        assertThat(order).containsEntry("status", "ACCEPTED");
        assertThat(new BigDecimal(order.get("limitPrice").toString()))
                .isEqualByComparingTo("70000");
    }

    // ── helpers ───────────────────────────────────────────────

    private static String newUser() {
        return UUID.randomUUID().toString();
    }

    private static Map<String, Object> marketBuy(String symbol, long quantity) {
        return body(symbol, "BUY", "MARKET", quantity, null);
    }

    private static Map<String, Object> marketSell(String symbol, long quantity) {
        return body(symbol, "SELL", "MARKET", quantity, null);
    }

    private static Map<String, Object> limitBuy(String symbol, long quantity, String limitPrice) {
        return body(symbol, "BUY", "LIMIT", quantity, limitPrice);
    }

    private static Map<String, Object> limitSell(String symbol, long quantity, String limitPrice) {
        return body(symbol, "SELL", "LIMIT", quantity, limitPrice);
    }

    private static Map<String, Object> body(String symbol, String side, String orderType,
            long quantity, String limitPrice) {
        Map<String, Object> body = new HashMap<>();
        body.put("symbol", symbol);
        body.put("side", side);
        body.put("orderType", orderType);
        body.put("quantity", quantity);
        body.put("limitPrice", limitPrice == null ? null : new BigDecimal(limitPrice));
        return body;
    }

    private ResponseEntity<Map<String, Object>> place(String userId, Map<String, Object> body) {
        return place(userId, UUID.randomUUID().toString(), body);
    }

    private ResponseEntity<Map<String, Object>> place(String userId, String idempotencyKey,
            Map<String, Object> body) {
        HttpHeaders headers = userHeaders(userId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WtsHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        return rest.exchange("/api/trading/orders", HttpMethod.POST,
                new HttpEntity<>(body, headers), mapType());
    }

    private ResponseEntity<Map<String, Object>> cancel(String userId, Integer orderId) {
        return rest.exchange("/api/trading/orders/" + orderId, HttpMethod.DELETE,
                new HttpEntity<>(userHeaders(userId)), mapType());
    }

    private List<Map<String, Object>> orders(String userId) {
        return rest.exchange("/api/trading/orders", HttpMethod.GET,
                new HttpEntity<>(userHeaders(userId)), listType()).getBody();
    }

    private List<Map<String, Object>> positions(String userId) {
        return rest.exchange("/api/trading/positions", HttpMethod.GET,
                new HttpEntity<>(userHeaders(userId)), listType()).getBody();
    }

    private Map<String, Object> onlyPosition(String userId) {
        List<Map<String, Object>> all = positions(userId);
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private Map<String, Object> account(String userId) {
        return rest.exchange("/api/trading/account", HttpMethod.GET,
                new HttpEntity<>(userHeaders(userId)), mapType()).getBody();
    }

    private BigDecimal cashBalance(String userId) {
        return new BigDecimal(account(userId).get("cashBalance").toString());
    }

    private BigDecimal reservedCash(String userId) {
        return new BigDecimal(account(userId).get("reservedCash").toString());
    }

    private BigDecimal availableCash(String userId) {
        return new BigDecimal(account(userId).get("availableCash").toString());
    }

    private static HttpHeaders userHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        // Gateway가 토큰을 검증한 뒤 심어주는 헤더다 (CLAUDE.md §6.1).
        headers.set(WtsHeaders.USER_ID, userId);
        return headers;
    }

    private static ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }

    private static ParameterizedTypeReference<List<Map<String, Object>>> listType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
