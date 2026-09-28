package com.team.wts.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.team.wts.common.auth.MockAuthToken;
import com.team.wts.common.web.WtsHeaders;
import com.team.wts.gateway.config.GatewayAuthProperties;

import reactor.core.publisher.Mono;

/**
 * Gateway 인증 필터. (CLAUDE.md §6.1)
 *
 * <p>downstream 서비스는 {@code X-User-Id} 헤더만 믿고 동작하므로,
 * 이 필터가 뚫리면 전체 인증이 무의미해진다. 사칭 차단을 특히 집중해서 검증한다.
 */
class AuthenticationWebFilterTest {

    private static final String SECRET = "gateway-filter-unit-test-secret";
    private static final String USER_ID = "3f1a6d2e-0c77-4a2f-9a1b-2b6d0a7e5c31";
    private static final String PROTECTED_PATH = "/api/trading/account";

    private final MockAuthToken tokens = new MockAuthToken(SECRET);
    // Spring Boot가 만드는 것과 같은 설정(JavaTimeModule 포함)을 써야
    // ErrorResponse의 Instant 필드가 직렬화된다.
    private final AuthenticationWebFilter filter = new AuthenticationWebFilter(
            tokens,
            Jackson2ObjectMapperBuilder.json().build(),
            new GatewayAuthProperties(List.of("/api/users/mock-login", "/actuator/**")));

    @Test
    @DisplayName("유효한 토큰이면 downstream 요청에 X-User-Id를 심는다")
    void injectsUserIdForValidToken() {
        var captured = new AtomicReference<ServerWebExchange>();
        var exchange = exchange(PROTECTED_PATH, bearer(USER_ID), null);

        filter.filter(exchange, capturing(captured)).block();

        assertThat(userIdSeenByDownstream(captured)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("클라이언트가 X-User-Id를 직접 보내도 무시하고 제거한다")
    void stripsClientSuppliedUserIdOnPublicPath() {
        var captured = new AtomicReference<ServerWebExchange>();
        var exchange = exchange("/api/users/mock-login", null, "attacker-user-id");

        filter.filter(exchange, capturing(captured)).block();

        assertThat(userIdSeenByDownstream(captured)).isNull();
    }

    @Test
    @DisplayName("토큰의 사용자가 이기고, 클라이언트가 보낸 X-User-Id는 버려진다")
    void tokenWinsOverClientSuppliedUserId() {
        var captured = new AtomicReference<ServerWebExchange>();
        var exchange = exchange(PROTECTED_PATH, bearer(USER_ID), "attacker-user-id");

        filter.filter(exchange, capturing(captured)).block();

        assertThat(userIdSeenByDownstream(captured)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401로 끊고 downstream까지 가지 않는다")
    void rejectsRequestWithoutAuthorizationHeader() {
        var captured = new AtomicReference<ServerWebExchange>();
        var exchange = exchange(PROTECTED_PATH, null, null);

        filter.filter(exchange, capturing(captured)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(captured.get()).isNull();
    }

    @Test
    @DisplayName("서명이 맞지 않는 토큰은 401로 끊는다")
    void rejectsTokenSignedWithAnotherSecret() {
        String forged = new MockAuthToken("another-secret").issue(USER_ID, Duration.ofHours(1));
        var captured = new AtomicReference<ServerWebExchange>();
        var exchange = exchange(PROTECTED_PATH, "Bearer " + forged, null);

        filter.filter(exchange, capturing(captured)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(captured.get()).isNull();
    }

    @Test
    @DisplayName("Bearer 접두어가 없으면 401로 끊는다")
    void rejectsNonBearerAuthorization() {
        var captured = new AtomicReference<ServerWebExchange>();
        var exchange = exchange(PROTECTED_PATH, "Basic dXNlcjpwYXNz", null);

        filter.filter(exchange, capturing(captured)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(captured.get()).isNull();
    }

    @Test
    @DisplayName("public 경로는 토큰 없이 통과한다")
    void allowsPublicPathWithoutToken() {
        var captured = new AtomicReference<ServerWebExchange>();
        var exchange = exchange("/actuator/health", null, null);

        filter.filter(exchange, capturing(captured)).block();

        assertThat(captured.get()).isNotNull();
    }

    // ── helpers ────────────────────────────────────────────

    private String bearer(String userId) {
        return "Bearer " + tokens.issue(userId, Duration.ofHours(1));
    }

    private static MockServerWebExchange exchange(String path, String authorization, String userIdHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (userIdHeader != null) {
            builder.header(WtsHeaders.USER_ID, userIdHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private static WebFilterChain capturing(AtomicReference<ServerWebExchange> sink) {
        return exchange -> {
            sink.set(exchange);
            return Mono.empty();
        };
    }

    private static String userIdSeenByDownstream(AtomicReference<ServerWebExchange> captured) {
        return captured.get().getRequest().getHeaders().getFirst(WtsHeaders.USER_ID);
    }
}
