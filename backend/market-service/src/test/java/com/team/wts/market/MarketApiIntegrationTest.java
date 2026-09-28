package com.team.wts.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.team.wts.market.quote.adapter.out.provider.MockMarketDataAdapter;
import com.team.wts.market.support.IntegrationTestBase;

/**
 * Phase 2 완료조건 확인. (CLAUDE.md §47)
 *
 * <p>시세 한 틱이 Valkey · Kafka · 1분봉까지 흘러가고 REST로 읽히는지를 실제 인프라에서 검증한다.
 */
class MarketApiIntegrationTest extends IntegrationTestBase {

    private static final String SAMSUNG = "005930";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MockMarketDataAdapter mockMarket;

    @BeforeEach
    void generateOneTick() {
        mockMarket.tick();
    }

    @Test
    @DisplayName("종목 목록은 설정된 5개 종목을 돌려준다")
    void listsConfiguredStocks() {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                "/api/market/stocks", org.springframework.http.HttpMethod.GET, null,
                new ParameterizedTypeReference<>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(5);
        assertThat(response.getBody()).anySatisfy(stock -> {
            assertThat(stock.get("symbol")).isEqualTo(SAMSUNG);
            assertThat(stock.get("name")).isEqualTo("삼성전자");
        });
    }

    @Test
    @DisplayName("종목명으로 검색한다")
    void searchesByName() {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                "/api/market/stocks?keyword=삼성", org.springframework.http.HttpMethod.GET, null,
                new ParameterizedTypeReference<>() { });

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("symbol")).isEqualTo(SAMSUNG);
    }

    @Test
    @DisplayName("종목코드로 검색한다")
    void searchesBySymbol() {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                "/api/market/stocks?keyword=00066", org.springframework.http.HttpMethod.GET, null,
                new ParameterizedTypeReference<>() { });

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("symbol")).isEqualTo("000660");
    }

    @Test
    @DisplayName("없는 종목은 404 SYMBOL_NOT_FOUND")
    void returnsNotFoundForUnknownSymbol() {
        ResponseEntity<Map<String, Object>> response = get("/api/market/stocks/999999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("SYMBOL_NOT_FOUND");
    }

    @Test
    @DisplayName("현재가는 Valkey에 저장된 최신 시세를 돌려준다")
    void returnsLatestPriceFromCache() {
        ResponseEntity<Map<String, Object>> response = get("/api/market/stocks/" + SAMSUNG + "/price");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("symbol")).isEqualTo(SAMSUNG);
        assertThat(new BigDecimal(body.get("price").toString())).isPositive();
        // 전일 종가를 숫자로 고정하지 않는다. Mock 모드에서는 설정의 출발 가격이고,
        // kis 모드에서는 실제 전일 종가라 값 자체는 환경에 따라 다르다.
        assertThat(new BigDecimal(body.get("previousClose").toString())).isPositive();
        // change = price - previousClose 가 맞아야 한다.
        assertThat(new BigDecimal(body.get("change").toString()))
                .isEqualByComparingTo(new BigDecimal(body.get("price").toString())
                        .subtract(new BigDecimal(body.get("previousClose").toString())));
        assertThat(((Number) body.get("volume")).longValue()).isPositive();
    }

    @Test
    @DisplayName("호가는 매도 5단계·매수 5단계를 돌려준다")
    void returnsOrderBook() {
        ResponseEntity<Map<String, Object>> response =
                get("/api/market/stocks/" + SAMSUNG + "/orderbook");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("asks")).hasSize(5);
        assertThat((List<?>) response.getBody().get("bids")).hasSize(5);
    }

    @Test
    @DisplayName("시세가 Kafka를 거쳐 1분봉으로 집계된다")
    void aggregatesCandlesFromKafkaStream() {
        // 여러 틱을 넣어 고가/저가가 갈리게 한다.
        for (int i = 0; i < 5; i++) {
            mockMarket.tick();
        }

        awaitUntil(Duration.ofSeconds(30), () -> {
            ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                    "/api/market/stocks/" + SAMSUNG + "/candles?interval=1m",
                    org.springframework.http.HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() { });
            return response.getStatusCode() == HttpStatus.OK
                    && response.getBody() != null
                    && !response.getBody().isEmpty();
        });

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                "/api/market/stocks/" + SAMSUNG + "/candles?interval=1m",
                org.springframework.http.HttpMethod.GET, null,
                new ParameterizedTypeReference<>() { });

        Map<String, Object> candle = response.getBody().get(response.getBody().size() - 1);
        BigDecimal high = new BigDecimal(candle.get("high").toString());
        BigDecimal low = new BigDecimal(candle.get("low").toString());
        BigDecimal open = new BigDecimal(candle.get("open").toString());
        BigDecimal close = new BigDecimal(candle.get("close").toString());

        assertThat(high).isGreaterThanOrEqualTo(low);
        assertThat(high).isGreaterThanOrEqualTo(open).isGreaterThanOrEqualTo(close);
        assertThat(low).isLessThanOrEqualTo(open).isLessThanOrEqualTo(close);
    }

    @Test
    @DisplayName("지원하지 않는 interval은 400 VALIDATION_FAILED")
    void rejectsUnsupportedInterval() {
        ResponseEntity<Map<String, Object>> response =
                get("/api/market/stocks/" + SAMSUNG + "/candles?interval=5m");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("limit 범위를 벗어나면 400")
    void rejectsOutOfRangeLimit() {
        assertThat(get("/api/market/stocks/" + SAMSUNG + "/candles?limit=0").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/market/stocks/" + SAMSUNG + "/candles?limit=5000").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map<String, Object>> get(String path) {
        return rest.exchange(path, org.springframework.http.HttpMethod.GET, null,
                new ParameterizedTypeReference<>() { });
    }
}
