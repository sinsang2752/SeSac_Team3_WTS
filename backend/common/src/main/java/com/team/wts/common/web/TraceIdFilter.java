package com.team.wts.common.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gateway가 심어준 X-Trace-Id를 MDC에 올려 구조화 로그에 남긴다. (CLAUDE.md §40)
 *
 * <p>서비스를 직접 호출하는 경우(로컬 테스트 등)에는 헤더가 없으므로 여기서 생성한다.
 *
 * <p>servlet 기반 서비스 전용이다. 리액티브인 gateway-service는
 * 자체 {@code TraceIdWebFilter}를 사용한다 (ADR-0003 참고).
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(WtsHeaders.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, traceId);
        response.setHeader(WtsHeaders.TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 스레드 재사용 시 traceId가 다음 요청으로 새지 않도록 반드시 제거한다.
            MDC.remove(MDC_KEY);
        }
    }
}
