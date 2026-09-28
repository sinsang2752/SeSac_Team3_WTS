package com.team.wts.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.team.wts.common.web.WtsHeaders;

/**
 * Phase 0 완료조건 검증: 서비스가 기동되고 health endpoint가 200을 반환한다.
 *
 * <p>gateway-service는 외부 인프라(MySQL/Kafka/Valkey) 의존이 없어
 * 인프라 없이도 health 200을 그대로 검증할 수 있다.
 */
// 운영 설정은 AUTH_TOKEN_SECRET 환경변수를 요구한다. 테스트는 고정값을 주입한다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "wts.auth.secret=gateway-test-secret-not-used-anywhere-else")
class GatewayHealthTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("health endpoint는 200 UP을 반환한다")
    void healthEndpointReturnsUp() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("모든 응답에 traceId가 부여된다")
    void assignsTraceIdToEveryResponse() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(WtsHeaders.TRACE_ID);
    }

    @Test
    @DisplayName("클라이언트가 보낸 traceId는 그대로 유지된다")
    void preservesClientProvidedTraceId() {
        String given = "11111111-2222-3333-4444-555555555555";

        webTestClient.get().uri("/actuator/health")
                .header(WtsHeaders.TRACE_ID, given)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(WtsHeaders.TRACE_ID, given);
    }
}
