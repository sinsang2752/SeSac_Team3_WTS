package com.team.wts.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.team.wts.common.auth.MockAuthToken;
import com.team.wts.common.web.WtsHeaders;
import com.team.wts.user.support.IntegrationTestBase;

/** Mock Login과 사용자 조회. (CLAUDE.md §47 Phase 1, §24) */
class UserApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MockAuthToken tokens;

    @Test
    @DisplayName("Mock 로그인하면 사용자가 만들어지고 토큰이 발급된다")
    void mockLoginCreatesUserAndIssuesToken() {
        ResponseEntity<Map<String, Object>> response = login("alice@wts.local", "앨리스");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("email")).isEqualTo("alice@wts.local");
        assertThat(body.get("nickname")).isEqualTo("앨리스");
        assertThat(body.get("userId")).asString().isNotBlank();

        // 발급된 토큰은 그 사용자의 것이어야 한다.
        assertThat(tokens.verify((String) body.get("accessToken")))
                .contains((String) body.get("userId"));
    }

    @Test
    @DisplayName("같은 이메일로 다시 로그인하면 같은 사용자를 돌려준다")
    void reusesExistingUserForSameEmail() {
        String first = (String) login("bob@wts.local", "밥").getBody().get("userId");
        String second = (String) login("bob@wts.local", "다른닉").getBody().get("userId");

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("이메일 대소문자가 달라도 같은 사용자로 취급한다")
    void normalizesEmailCase() {
        String lower = (String) login("carol@wts.local", "캐롤").getBody().get("userId");
        String upper = (String) login("CAROL@WTS.LOCAL", "캐롤").getBody().get("userId");

        assertThat(upper).isEqualTo(lower);
    }

    @Test
    @DisplayName("닉네임을 비우면 이메일 앞부분을 쓴다")
    void fallsBackToEmailLocalPartAsNickname() {
        ResponseEntity<Map<String, Object>> response = login("dave@wts.local", null);

        assertThat(response.getBody().get("nickname")).isEqualTo("dave");
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400 VALIDATION_FAILED")
    void rejectsInvalidEmail() {
        ResponseEntity<Map<String, Object>> response = login("not-an-email", "x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("GET /me 는 X-User-Id 로 본인 정보를 돌려준다")
    void returnsCurrentUser() {
        String userId = (String) login("erin@wts.local", "에린").getBody().get("userId");

        ResponseEntity<Map<String, Object>> response = getMe(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("userId")).isEqualTo(userId);
        assertThat(response.getBody().get("email")).isEqualTo("erin@wts.local");
    }

    @Test
    @DisplayName("X-User-Id 가 없으면 401 UNAUTHORIZED")
    void rejectsRequestWithoutUserHeader() {
        ResponseEntity<Map<String, Object>> response = exchange("/api/users/me", new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 404 USER_NOT_FOUND")
    void returnsNotFoundForUnknownUser() {
        ResponseEntity<Map<String, Object>> response =
                getMe("00000000-0000-0000-0000-000000000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("에러 응답에도 traceId가 실린다")
    void errorResponseCarriesTraceId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(WtsHeaders.TRACE_ID, "11111111-2222-3333-4444-555555555555");

        ResponseEntity<Map<String, Object>> response = exchange("/api/users/me", headers);

        assertThat(response.getBody().get("traceId"))
                .isEqualTo("11111111-2222-3333-4444-555555555555");
    }

    // ── helpers ────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> login(String email, String nickname) {
        return rest.exchange(
                "/api/users/mock-login",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "nickname", nickname == null ? "" : nickname)),
                new org.springframework.core.ParameterizedTypeReference<>() { });
    }

    private ResponseEntity<Map<String, Object>> getMe(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(WtsHeaders.USER_ID, userId);
        return exchange("/api/users/me", headers);
    }

    private ResponseEntity<Map<String, Object>> exchange(String path, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers),
                new org.springframework.core.ParameterizedTypeReference<>() { });
    }
}
