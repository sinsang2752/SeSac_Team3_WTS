package com.team.wts.trading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.team.wts.common.web.WtsHeaders;
import com.team.wts.trading.support.IntegrationTestBase;

/**
 * Phase 1 완료조건: {@code GET /api/trading/account} 에서 1억원이 확인된다. (CLAUDE.md §47)
 */
class AccountApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("계좌를 처음 조회하면 초기 가상자금 1억원으로 개설된다")
    void opensAccountWithOneHundredMillionOnFirstAccess() {
        ResponseEntity<Map<String, Object>> response = getAccount(UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        // 금액 비교에 부동소수를 쓰지 않는다 (CLAUDE.md §42).
        assertThat(new BigDecimal(body.get("cashBalance").toString()))
                .isEqualByComparingTo(DEFAULT_INITIAL_CASH);
        assertThat(body.get("accountId")).isNotNull();
    }

    @Test
    @DisplayName("개설 직후 주문 가능 금액은 예수금과 같고 예약 금액은 0이다")
    void availableCashEqualsCashBalanceWhenNothingReserved() {
        Map<String, Object> body = getAccount(UUID.randomUUID().toString()).getBody();

        assertThat(new BigDecimal(body.get("reservedCash").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new BigDecimal(body.get("availableCash").toString()))
                .isEqualByComparingTo(new BigDecimal(body.get("cashBalance").toString()));
    }

    @Test
    @DisplayName("같은 사용자가 다시 조회해도 계좌가 새로 만들어지지 않는다")
    void doesNotCreateDuplicateAccountForSameUser() {
        String userId = UUID.randomUUID().toString();

        Object first = getAccount(userId).getBody().get("accountId");
        Object second = getAccount(userId).getBody().get("accountId");

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("사용자마다 별도의 계좌가 만들어진다")
    void opensSeparateAccountPerUser() {
        Object a = getAccount(UUID.randomUUID().toString()).getBody().get("accountId");
        Object b = getAccount(UUID.randomUUID().toString()).getBody().get("accountId");

        assertThat(b).isNotEqualTo(a);
    }

    @Test
    @DisplayName("X-User-Id 가 없으면 401 UNAUTHORIZED")
    void rejectsUnauthenticatedRequest() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/trading/account", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    private ResponseEntity<Map<String, Object>> getAccount(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(WtsHeaders.USER_ID, userId);
        return rest.exchange("/api/trading/account", HttpMethod.GET,
                new HttpEntity<>(headers), new ParameterizedTypeReference<>() { });
    }
}
