package com.team.wts.gateway.filter;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.wts.common.auth.MockAuthToken;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.common.error.ErrorResponse;
import com.team.wts.common.web.WtsHeaders;
import com.team.wts.gateway.config.GatewayAuthProperties;

import reactor.core.publisher.Mono;

/**
 * Gateway 인증 필터. (CLAUDE.md §6.1)
 *
 * <p>{@code Authorization: Bearer <token>} 을 검증하고, 통과하면 downstream 요청에
 * {@code X-User-Id} 를 심는다. downstream 서비스는 이 헤더만 믿고 동작한다.
 *
 * <p>따라서 <b>클라이언트가 보낸 X-User-Id는 항상 제거한다</b>.
 * 그러지 않으면 누구나 헤더 하나로 다른 사용자를 사칭할 수 있다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthenticationWebFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MockAuthToken tokens;
    private final ObjectMapper objectMapper;
    private final List<PathPattern> publicPaths;

    public AuthenticationWebFilter(MockAuthToken tokens,
                                   ObjectMapper objectMapper,
                                   GatewayAuthProperties properties) {
        this.tokens = tokens;
        this.objectMapper = objectMapper;
        PathPatternParser parser = new PathPatternParser();
        this.publicPaths = properties.publicPaths().stream().map(parser::parse).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 사칭 방지: 외부에서 들어온 X-User-Id는 무조건 버린다.
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(WtsHeaders.USER_ID))
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitized).build();

        if (isPublic(exchange)) {
            return chain.filter(sanitizedExchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Authorization 헤더가 없습니다.");
        }

        return tokens.verify(authorization.substring(BEARER_PREFIX.length()))
                .map(userId -> chain.filter(sanitizedExchange.mutate()
                        .request(sanitized.mutate().header(WtsHeaders.USER_ID, userId).build())
                        .build()))
                .orElseGet(() -> unauthorized(exchange, "토큰이 유효하지 않거나 만료되었습니다."));
    }

    private boolean isPublic(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        return publicPaths.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String traceId = response.getHeaders().getFirst(WtsHeaders.TRACE_ID);
        ErrorResponse body = ErrorResponse.of(ErrorCode.UNAUTHORIZED, message, traceId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // ErrorResponse는 단순 record다. 여기 도달하면 직렬화 설정이 깨진 것이다.
            throw new IllegalStateException("에러 응답 직렬화에 실패했다.", e);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
