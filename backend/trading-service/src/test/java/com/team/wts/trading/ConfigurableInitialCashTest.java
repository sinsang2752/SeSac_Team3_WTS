package com.team.wts.trading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.team.wts.common.web.WtsHeaders;
import com.team.wts.trading.support.IntegrationTestBase;

/**
 * 초기 가상자금은 설정값으로 바꿀 수 있어야 한다. (CLAUDE.md §9.1)
 */
// webEnvironment를 다시 적어주지 않으면 베이스의 RANDOM_PORT 설정이 덮여 서버가 뜨지 않는다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "trading.initial-cash=50000000")
class ConfigurableInitialCashTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("trading.initial-cash 를 바꾸면 그 금액으로 계좌가 개설된다")
    void honoursConfiguredInitialCash() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(WtsHeaders.USER_ID, UUID.randomUUID().toString());

        Map<String, Object> body = rest.exchange("/api/trading/account", HttpMethod.GET,
                new HttpEntity<>(headers), new ParameterizedTypeReference<Map<String, Object>>() { })
                .getBody();

        assertThat(new BigDecimal(body.get("cashBalance").toString()))
                .isEqualByComparingTo(new BigDecimal("50000000"));
    }
}
