package com.team.wts.common.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.team.wts.common.auth.AuthProperties;
import com.team.wts.common.auth.MockAuthToken;

/**
 * 토큰 발급/검증기를 등록한다. servlet · reactive 양쪽 모두에서 동작한다.
 *
 * <p>토큰을 다루는 서비스는 gateway(검증)와 user(발급) 둘뿐이다.
 * market / trading은 gateway가 심어준 {@code X-User-Id}만 읽으므로 비밀키가 필요 없다.
 * 그래서 {@code wts.auth.secret}이 설정된 서비스에서만 빈을 만든다.
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
public class WtsAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wts.auth", name = "secret")
    public MockAuthToken mockAuthToken(AuthProperties properties) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException(
                    "환경변수 AUTH_TOKEN_SECRET 이 설정되지 않았다. "
                            + ".env 를 만들고(cp .env.example .env) 값을 채운 뒤 다시 실행한다.");
        }
        return new MockAuthToken(properties.secret());
    }
}
