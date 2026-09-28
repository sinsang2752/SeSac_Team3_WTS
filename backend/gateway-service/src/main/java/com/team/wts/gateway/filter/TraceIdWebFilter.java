package com.team.wts.gateway.filter;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.team.wts.common.web.WtsHeaders;

import reactor.core.publisher.Mono;

/**
 * 모든 요청에 traceId를 부여한다. (CLAUDE.md §6.1, §40)
 *
 * <p>Spring Cloud Gateway의 {@code GlobalFilter}가 아니라 {@code WebFilter}를 사용한다.
 * GlobalFilter는 라우트에 매칭된 요청에만 동작해서 actuator 같은 로컬 엔드포인트를 놓친다.
 * WebFilter는 게이트웨이를 통과하는 모든 요청을 덮으며, 여기서 변형한 exchange가
 * 그대로 라우팅 체인으로 전달되므로 downstream 서비스도 같은 traceId를 받는다.
 *
 * <p>MDC는 쓰지 않는다. 리액티브 체인은 스레드를 넘나들기 때문에 MDC 값이 누락되거나
 * 이벤트 루프 스레드를 공유하는 다른 요청으로 샐 수 있다. 대신 Reactor Context에 담는다.
 * 리액티브 로그 상관관계는 MVP 이후 OpenTelemetry 도입 시 마무리한다 (CLAUDE.md §48).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    public static final String TRACE_ID_CONTEXT_KEY = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(WtsHeaders.TRACE_ID);
        String traceId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(WtsHeaders.TRACE_ID, traceId)
                .build();
        exchange.getResponse().getHeaders().set(WtsHeaders.TRACE_ID, traceId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .contextWrite(context -> context.put(TRACE_ID_CONTEXT_KEY, traceId));
    }
}
