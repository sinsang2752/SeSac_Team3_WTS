package com.team.wts.trading;

import static com.team.wts.trading.support.TradingApiClient.decimal;
import static com.team.wts.trading.support.TradingApiClient.order;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team.wts.trading.support.IntegrationTestBase;
import com.team.wts.trading.support.MarketPriceFixture;
import com.team.wts.trading.support.TradingApiClient;

/** 포트폴리오 평가. (CLAUDE.md §24, §47 Phase 5, §49) */
class PortfolioApiIntegrationTest extends IntegrationTestBase {

    private static final String SAMSUNG = "005930";
    private static final String HYNIX = "000660";

    @Autowired
    private TradingApiClient api;

    @Autowired
    private MarketPriceFixture marketPrice;

    @Test
    @DisplayName("보유 종목이 없으면 총자산은 예수금과 같다")
    void emptyPortfolioIsAllCash() {
        Map<String, Object> portfolio = api.get(UUID.randomUUID().toString(), "/api/trading/portfolio");

        assertThat(portfolio.get("positions")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
        assertThat(decimal(portfolio, "totalEvaluationAmount")).isEqualByComparingTo("0");
        assertThat(decimal(portfolio, "valuationProfit")).isEqualByComparingTo("0");
        assertThat(decimal(portfolio, "valuationProfitRate")).isEqualByComparingTo("0");
        assertThat(decimal(portfolio, "totalAssets")).isEqualByComparingTo(DEFAULT_INITIAL_CASH);
    }

    @Test
    @DisplayName("계좌가 없는 사용자가 어떤 조회를 먼저 해도 열린다")
    void anyQueryProvisionsTheAccount() {
        // 계좌는 최초 조회 시점에 만들어진다(ADR-0005). 조회 경로가 읽기 전용 트랜잭션이면
        // 그 INSERT가 실패한다. 네 경로 모두 첫 호출이 성공해야 한다.
        for (String path : List.of("/api/trading/portfolio", "/api/trading/positions",
                "/api/trading/orders", "/api/trading/executions")) {
            String freshUser = UUID.randomUUID().toString();
            assertThat(api.rawGet(freshUser, path).getStatusCode())
                    .as(path)
                    .isEqualTo(org.springframework.http.HttpStatus.OK);
        }
    }

    @Test
    @DisplayName("현재가가 오르면 평가손익이 잡힌다")
    void evaluatesAtCurrentPrice() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null));

        // 매수 후 가격이 올랐다.
        marketPrice.publish(SAMSUNG, "88000");
        Map<String, Object> portfolio = api.get(user, "/api/trading/portfolio");

        assertThat(decimal(portfolio, "totalPurchaseAmount")).isEqualByComparingTo("800000");
        assertThat(decimal(portfolio, "totalEvaluationAmount")).isEqualByComparingTo("880000");
        assertThat(decimal(portfolio, "valuationProfit")).isEqualByComparingTo("80000");
        assertThat(decimal(portfolio, "valuationProfitRate")).isEqualByComparingTo("10.00");
        // 총자산 = 예수금(1억 - 80만) + 평가금액 88만
        assertThat(decimal(portfolio, "totalAssets")).isEqualByComparingTo("100080000");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) portfolio.get("positions");
        assertThat(items).hasSize(1);
        assertThat(decimal(items.get(0), "currentPrice")).isEqualByComparingTo("88000");
        assertThat(decimal(items.get(0), "valuationProfitRate")).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("여러 종목을 한 번에 평가한다")
    void evaluatesMultipleSymbols() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        marketPrice.publish(HYNIX, "180000");
        api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null));
        api.place(user, order(HYNIX, "BUY", "MARKET", 2, null));

        marketPrice.publish(SAMSUNG, "70000");   // -10만
        marketPrice.publish(HYNIX, "200000");    // +4만
        Map<String, Object> portfolio = api.get(user, "/api/trading/portfolio");

        assertThat(decimal(portfolio, "totalPurchaseAmount")).isEqualByComparingTo("1160000");
        assertThat(decimal(portfolio, "totalEvaluationAmount")).isEqualByComparingTo("1100000");
        assertThat(decimal(portfolio, "valuationProfit")).isEqualByComparingTo("-60000");
    }

    @Test
    @DisplayName("실현손익은 평가손익과 따로 집계된다")
    void tracksRealizedProfitSeparately() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null));
        marketPrice.publish(SAMSUNG, "90000");
        api.place(user, order(SAMSUNG, "SELL", "MARKET", 4, null));

        Map<String, Object> portfolio = api.get(user, "/api/trading/portfolio");

        // 판 4주: (90,000 - 80,000) × 4 = 40,000
        assertThat(decimal(portfolio, "realizedProfit")).isEqualByComparingTo("40000");
        // 남은 6주: (90,000 - 80,000) × 6 = 60,000
        assertThat(decimal(portfolio, "valuationProfit")).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("시세를 못 읽은 종목은 매입가로 평가한다")
    void fallsBackToPurchasePriceWithoutQuote() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null));

        // market-service가 죽어 캐시가 사라진 상황.
        marketPrice.clear(SAMSUNG);
        Map<String, Object> portfolio = api.get(user, "/api/trading/portfolio");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) portfolio.get("positions");
        assertThat(items.get(0).get("currentPrice")).isNull();
        // 모르는 값을 0으로 만들어 총자산을 왜곡하지 않는다.
        assertThat(decimal(portfolio, "totalEvaluationAmount")).isEqualByComparingTo("800000");
        assertThat(decimal(portfolio, "valuationProfit")).isEqualByComparingTo("0");
        assertThat(decimal(portfolio, "totalAssets")).isEqualByComparingTo(DEFAULT_INITIAL_CASH);
    }

    @Test
    @DisplayName("미체결 매도로 묶인 수량도 보유 수량에 포함된다")
    void includesReservedQuantity() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null));
        api.place(user, order(SAMSUNG, "SELL", "LIMIT", 4, "99000"));

        Map<String, Object> portfolio = api.get(user, "/api/trading/portfolio");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) portfolio.get("positions");
        assertThat(items.get(0)).containsEntry("quantity", 10);
        assertThat(items.get(0)).containsEntry("reservedQuantity", 4);
        assertThat(items.get(0)).containsEntry("availableQuantity", 6);
        // 묶여 있어도 자산이다.
        assertThat(decimal(portfolio, "totalEvaluationAmount")).isEqualByComparingTo("800000");
    }
}
