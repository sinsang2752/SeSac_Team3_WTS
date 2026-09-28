package com.team.wts.user;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.team.wts.user.support.IntegrationTestBase;

/** 관심종목 API. (CLAUDE.md §24, §47 Phase 5) */
class WatchlistApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("담고, 조회하고, 뺀다")
    void addListRemove() {
        String userId = newUser();

        assertThat(list(userId)).isEmpty();

        assertThat(add(userId, "005930").getStatusCode()).isEqualTo(HttpStatus.OK);
        add(userId, "000660");

        assertThat(list(userId)).extracting(item -> item.get("symbol"))
                .containsExactly("005930", "000660");

        assertThat(remove(userId, "005930").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(list(userId)).extracting(item -> item.get("symbol")).containsExactly("000660");
    }

    @Test
    @DisplayName("같은 종목을 두 번 담아도 하나다")
    void addingTwiceKeepsOne() {
        String userId = newUser();

        add(userId, "035420");
        ResponseEntity<Map<String, Object>> second = add(userId, "035420");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list(userId)).hasSize(1);
    }

    @Test
    @DisplayName("담겨 있지 않은 종목을 빼도 오류가 아니다")
    void removingAbsentSymbolSucceeds() {
        String userId = newUser();

        assertThat(remove(userId, "005380").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(list(userId)).isEmpty();
    }

    @Test
    @DisplayName("종목코드 형식이 틀리면 400이다")
    void rejectsMalformedSymbol() {
        ResponseEntity<Map<String, Object>> response = add(newUser(), "SAMSUNG");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("관심종목은 사용자마다 따로 쌓인다")
    void isolatedPerUser() {
        String alice = newUser();
        String bob = newUser();

        add(alice, "005930");

        assertThat(list(alice)).hasSize(1);
        assertThat(list(bob)).isEmpty();
    }

    @Test
    @DisplayName("인증 없이는 접근할 수 없다")
    void requiresAuthentication() {
        ResponseEntity<Map<String, Object>> response = rest.exchange("/api/users/me/watchlist",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), map());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── helpers ──────────────────────────────────────────────

    /** watchlists가 users를 FK로 참조하므로 실제 사용자가 있어야 한다. */
    private String newUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("email", UUID.randomUUID() + "@wts.local");
        return (String) rest.exchange("/api/users/mock-login", HttpMethod.POST,
                new HttpEntity<>(body, headers), map()).getBody().get("userId");
    }

    private ResponseEntity<Map<String, Object>> add(String userId, String symbol) {
        return rest.exchange("/api/users/me/watchlist/" + symbol, HttpMethod.POST,
                new HttpEntity<>(headers(userId)), map());
    }

    private ResponseEntity<Map<String, Object>> remove(String userId, String symbol) {
        return rest.exchange("/api/users/me/watchlist/" + symbol, HttpMethod.DELETE,
                new HttpEntity<>(headers(userId)), map());
    }

    private List<Map<String, Object>> list(String userId) {
        return rest.exchange("/api/users/me/watchlist", HttpMethod.GET,
                new HttpEntity<>(headers(userId)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    private static HttpHeaders headers(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(WtsHeaders.USER_ID, userId);
        return headers;
    }

    private static ParameterizedTypeReference<Map<String, Object>> map() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
