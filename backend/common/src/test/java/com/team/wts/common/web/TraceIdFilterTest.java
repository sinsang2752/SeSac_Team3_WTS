package com.team.wts.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    @DisplayName("Gateway가 보낸 traceId를 그대로 사용한다")
    void reusesIncomingTraceId() throws Exception {
        String given = "11111111-2222-3333-4444-555555555555";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WtsHeaders.TRACE_ID, given);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(WtsHeaders.TRACE_ID)).isEqualTo(given);
    }

    @Test
    @DisplayName("traceId 헤더가 없으면 새로 생성한다")
    void generatesTraceIdWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(WtsHeaders.TRACE_ID)).isNotBlank();
    }

    @Test
    @DisplayName("요청 처리 중에는 MDC에 traceId가 올라가 있다")
    void putsTraceIdOnMdcDuringRequest() throws Exception {
        String given = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WtsHeaders.TRACE_ID, given);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain assertingChain =
                (req, res) -> assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isEqualTo(given);

        filter.doFilter(request, response, assertingChain);
    }

    @Test
    @DisplayName("요청이 끝나면 MDC를 비워 다음 요청으로 새지 않게 한다")
    void clearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("체인에서 예외가 나도 MDC는 정리된다")
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain throwingChain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(request, response, throwingChain);
        } catch (Exception expected) {
            // 예외 전파는 정상 동작이다. 여기서 확인할 것은 MDC 정리 여부다.
        }

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }
}
