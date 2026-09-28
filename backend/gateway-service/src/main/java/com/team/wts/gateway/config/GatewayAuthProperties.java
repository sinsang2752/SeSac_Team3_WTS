package com.team.wts.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 없이 통과시킬 경로.
 *
 * @param publicPaths Ant/PathPattern 문법. 예: {@code /api/users/mock-login}, {@code /actuator/**}
 */
@ConfigurationProperties(prefix = "wts.gateway.auth")
public record GatewayAuthProperties(List<String> publicPaths) {

    public GatewayAuthProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
