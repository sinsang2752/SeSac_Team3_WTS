package com.team.wts.common.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mock 인증 설정. 토큰을 발급하는 user-service와 검증하는 gateway-service가
 * 같은 비밀키를 공유해야 한다.
 *
 * @param secret   HMAC 서명 키. 환경변수 {@code AUTH_TOKEN_SECRET}로 주입한다.
 *                 기본값을 두지 않는다 — 코드에 비밀값을 남기지 않기 위해서다 (CLAUDE.md §52).
 * @param tokenTtl 발급 토큰 유효기간
 */
@ConfigurationProperties(prefix = "wts.auth")
public record AuthProperties(String secret, Duration tokenTtl) {

    public AuthProperties {
        if (tokenTtl == null) {
            tokenTtl = Duration.ofHours(24);
        }
    }
}
