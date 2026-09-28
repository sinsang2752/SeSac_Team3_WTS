package com.team.wts.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MockAuthTokenTest {

    private static final String SECRET = "test-secret-for-unit-tests-only";
    private static final String USER_ID = "3f1a6d2e-0c77-4a2f-9a1b-2b6d0a7e5c31";

    private final MockAuthToken tokens = new MockAuthToken(SECRET);

    @Test
    @DisplayName("발급한 토큰을 검증하면 원래 사용자 ID가 나온다")
    void issuesAndVerifies() {
        String token = tokens.issue(USER_ID, Duration.ofHours(1));

        assertThat(tokens.verify(token)).contains(USER_ID);
    }

    @Test
    @DisplayName("다른 비밀키로 검증하면 거절한다")
    void rejectsTokenSignedWithAnotherSecret() {
        String token = new MockAuthToken("another-secret").issue(USER_ID, Duration.ofHours(1));

        assertThat(tokens.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("payload를 변조하면 서명이 맞지 않아 거절한다")
    void rejectsTamperedPayload() {
        String token = tokens.issue(USER_ID, Duration.ofHours(1));
        String signature = token.substring(token.indexOf('.'));
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("attacker:" + (System.currentTimeMillis() / 1000 + 3600))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(tokens.verify(forgedPayload + signature)).isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰은 거절한다")
    void rejectsExpiredToken() {
        String expired = tokens.issue(USER_ID, Duration.ofSeconds(-1));

        assertThat(tokens.verify(expired)).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("형식이 깨진 토큰은 예외 없이 거절한다")
    @ValueSource(strings = {"", ".", "a.", ".b", "no-separator", "!!!.???", "a.b.c"})
    void rejectsMalformedToken(String malformed) {
        assertThat(tokens.verify(malformed)).isEmpty();
    }

    @Test
    @DisplayName("null 토큰은 거절한다")
    void rejectsNullToken() {
        assertThat(tokens.verify(null)).isEmpty();
    }

    @Test
    @DisplayName("구분자가 섞인 userId는 발급을 거부한다")
    void rejectsUserIdContainingDelimiter() {
        assertThatThrownBy(() -> tokens.issue("user:123", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀키가 비어 있으면 생성 시점에 실패한다")
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> new MockAuthToken("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
