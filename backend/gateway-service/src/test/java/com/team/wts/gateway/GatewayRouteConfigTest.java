package com.team.wts.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

/**
 * 라우팅 설정 회귀 방지. (CLAUDE.md §6.1, §25)
 *
 * <p>WebSocket 라우트에 응답 헤더를 수정하는 필터가 붙으면 업그레이드 이후 헤더가 읽기 전용이 되어
 * {@code UnsupportedOperationException}으로 연결이 끊긴다. 실제로 겪은 문제라 설정 수준에서 막는다.
 */
@SpringBootTest(properties = "wts.auth.secret=gateway-test-secret-not-used-anywhere-else")
class GatewayRouteConfigTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    private Map<String, RouteDefinition> routes() {
        List<RouteDefinition> definitions = routeDefinitionLocator.getRouteDefinitions()
                .collectList().block();
        assertThat(definitions).isNotNull();
        return definitions.stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));
    }

    @Test
    @DisplayName("CLAUDE.md §6.1의 4개 라우트가 등록된다")
    void registersAllRoutes() {
        assertThat(routes().keySet()).containsExactlyInAnyOrder(
                "market-service", "trading-service", "user-service", "market-websocket");
    }

    @Test
    @DisplayName("WebSocket 라우트에는 응답 헤더를 수정하는 필터를 붙이지 않는다")
    void webSocketRouteHasNoResponseHeaderFilter() {
        RouteDefinition webSocket = routes().get("market-websocket");

        assertThat(webSocket.getUri().getScheme()).isEqualTo("ws");
        assertThat(webSocket.getFilters())
                .as("WebSocket 라우트의 필터는 업그레이드 이후 응답 헤더를 건드려 연결을 끊는다")
                .isEmpty();
    }

    @Test
    @DisplayName("HTTP 라우트에는 헤더 중복 제거 필터가 붙는다")
    void httpRoutesDeduplicateResponseHeaders() {
        for (String routeId : List.of("market-service", "trading-service", "user-service")) {
            assertThat(routes().get(routeId).getFilters())
                    .as("%s 라우트", routeId)
                    .anyMatch(filter -> filter.getName().equals("DedupeResponseHeader"));
        }
    }
}
