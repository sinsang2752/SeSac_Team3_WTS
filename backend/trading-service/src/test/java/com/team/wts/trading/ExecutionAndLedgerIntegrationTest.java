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

import com.team.wts.trading.ledger.domain.LedgerEntry;
import com.team.wts.trading.ledger.domain.LedgerEntryRepository;
import com.team.wts.trading.ledger.domain.LedgerEntryType;
import com.team.wts.trading.support.IntegrationTestBase;
import com.team.wts.trading.support.MarketPriceFixture;
import com.team.wts.trading.support.TradingApiClient;

/** 체결 기록과 원장. (CLAUDE.md §23, §24, §35) */
class ExecutionAndLedgerIntegrationTest extends IntegrationTestBase {

    private static final String SAMSUNG = "005930";

    @Autowired
    private TradingApiClient api;

    @Autowired
    private MarketPriceFixture marketPrice;

    @Autowired
    private LedgerEntryRepository ledger;

    @Test
    @DisplayName("계좌를 열면 초기 가상자금이 원장의 첫 줄로 남는다")
    void accountOpeningWritesInitialDeposit() {
        String user = UUID.randomUUID().toString();
        Long accountId = ((Number) api.account(user).get("accountId")).longValue();

        List<LedgerEntry> entries = ledger.findByAccountIdOrderByIdDesc(accountId);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).type()).isEqualTo(LedgerEntryType.INITIAL_DEPOSIT);
        assertThat(entries.get(0).beforeBalance()).isEqualByComparingTo("0");
        assertThat(entries.get(0).afterBalance()).isEqualByComparingTo(DEFAULT_INITIAL_CASH);
        assertThat(entries.get(0).orderId()).isNull();
        assertThat(entries.get(0).executionId()).isNull();
    }

    @Test
    @DisplayName("시장가 체결이 체결가를 남긴다. 주문만으로는 알 수 없는 값이다")
    void marketFillRecordsExecutionPrice() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80300");
        long orderId = ((Number) api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null))
                .getBody().get("orderId")).longValue();

        List<Map<String, Object>> executions = api.list(user, "/api/trading/executions");

        assertThat(executions).hasSize(1);
        Map<String, Object> execution = executions.get(0);
        assertThat(((Number) execution.get("orderId")).longValue()).isEqualTo(orderId);
        assertThat(execution).containsEntry("side", "BUY");
        assertThat(decimal(execution, "price")).isEqualByComparingTo("80300");
        assertThat(decimal(execution, "amount")).isEqualByComparingTo("803000");
        assertThat(decimal(execution, "fee")).isEqualByComparingTo("0");
        // 매수에는 실현손익이 없다.
        assertThat(execution).doesNotContainKey("realizedProfit");
    }

    @Test
    @DisplayName("매도 체결에 실현손익이 남는다 (CLAUDE.md §35)")
    void sellFillRecordsRealizedProfit() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null));
        marketPrice.publish(SAMSUNG, "90000");
        api.place(user, order(SAMSUNG, "SELL", "MARKET", 4, null));

        List<Map<String, Object>> executions = api.list(user, "/api/trading/executions");

        assertThat(executions).hasSize(2);
        Map<String, Object> sell = executions.get(0); // 최신순
        assertThat(sell).containsEntry("side", "SELL");
        // (90,000 - 80,000) × 4
        assertThat(decimal(sell, "realizedProfit")).isEqualByComparingTo("40000");
    }

    @Test
    @DisplayName("원장만으로 예수금을 재구성할 수 있다")
    void ledgerReconstructsCashBalance() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null));
        marketPrice.publish(SAMSUNG, "85000");
        api.place(user, order(SAMSUNG, "SELL", "MARKET", 3, null));

        Map<String, Object> account = api.account(user);
        Long accountId = ((Number) account.get("accountId")).longValue();
        List<LedgerEntry> entries = ledger.findByAccountIdOrderByIdDesc(accountId);

        assertThat(entries).extracting(LedgerEntry::type).containsExactly(
                LedgerEntryType.SELL, LedgerEntryType.BUY, LedgerEntryType.INITIAL_DEPOSIT);

        java.math.BigDecimal sum = entries.stream()
                .map(LedgerEntry::amount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(decimal(account, "cashBalance"));
        // 가장 최근 기록의 after_balance가 곧 현재 예수금이다.
        assertThat(entries.get(0).afterBalance()).isEqualByComparingTo(decimal(account, "cashBalance"));
    }

    @Test
    @DisplayName("거절된 주문은 체결도 원장도 남기지 않는다")
    void rejectedOrderLeavesNoExecutionOrLedger() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        api.place(user, order(SAMSUNG, "BUY", "MARKET", 100000, null));

        Long accountId = ((Number) api.account(user).get("accountId")).longValue();

        assertThat(api.list(user, "/api/trading/executions")).isEmpty();
        assertThat(ledger.findByAccountIdOrderByIdDesc(accountId))
                .extracting(LedgerEntry::type)
                .containsExactly(LedgerEntryType.INITIAL_DEPOSIT);
    }
}
