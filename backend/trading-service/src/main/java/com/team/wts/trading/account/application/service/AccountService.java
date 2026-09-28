package com.team.wts.trading.account.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import com.team.wts.trading.account.domain.VirtualAccount;
import com.team.wts.trading.account.domain.VirtualAccountRepository;
import com.team.wts.trading.config.TradingProperties;
import com.team.wts.trading.ledger.application.service.LedgerService;
import com.team.wts.trading.ledger.domain.LedgerEntryType;

/**
 * 가상 계좌 조회 및 개설. (CLAUDE.md §9.1, §24)
 *
 * <p><b>계좌는 최초 조회 시점에 만들어진다.</b> user-service가 사용자를 만들 때
 * trading-service가 계좌를 만드는 것이 이상적이지만, 그러려면 서비스 간 동기 호출이나
 * Kafka 이벤트가 필요하다. Kafka 연동은 Phase 4다 (§47).
 * Phase 1에서는 조회 시 없으면 만드는 방식으로 두고, Phase 4에서
 * {@code user.created} 이벤트 소비로 옮긴다. (ADR-0005)
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final VirtualAccountRepository accounts;
    private final TradingProperties properties;
    private final LedgerService ledger;

    public AccountService(VirtualAccountRepository accounts, TradingProperties properties,
            LedgerService ledger) {
        this.accounts = accounts;
        this.properties = properties;
        this.ledger = ledger;
    }

    /** 사용자의 계좌를 돌려준다. 없으면 초기 가상자금을 넣어 개설한다. */
    @Transactional
    public VirtualAccount getOrOpen(String userId) {
        return accounts.findByUserId(userId).orElseGet(() -> open(userId));
    }

    /**
     * 잔고를 바꿀 목적으로 계좌를 잡는다. 행에 비관적 락을 건다. (CLAUDE.md §13, ADR-0009)
     *
     * <p>주문 접수와 취소는 반드시 이 메서드로 시작한다. 한 사용자의 주문 처리가 직렬화되므로
     * 예수금 경합뿐 아니라 같은 Idempotency-Key의 동시 요청도 함께 막힌다.
     *
     * <p>계좌가 없어 새로 INSERT하는 경로에서는 락 대신 INSERT가 만든 행 잠금이 같은 역할을 한다.
     */
    @Transactional
    public VirtualAccount getOrOpenForUpdate(String userId) {
        return accounts.findByUserIdForUpdate(userId).orElseGet(() -> open(userId));
    }

    private VirtualAccount open(String userId) {
        VirtualAccount candidate = VirtualAccount.open(userId, properties.initialCash());
        try {
            VirtualAccount opened = accounts.save(candidate);
            // 초기 가상자금도 자산이 움직인 사건이다. 원장의 첫 줄이 된다 (CLAUDE.md §23).
            if (properties.initialCash().signum() > 0) {
                ledger.record(opened, null, null, LedgerEntryType.INITIAL_DEPOSIT,
                        properties.initialCash(), BigDecimal.ZERO);
            }
            log.info("가상 계좌 개설: userId={} accountId={} initialCash={}",
                    userId, opened.id(), properties.initialCash());
            return opened;
        } catch (DataIntegrityViolationException e) {
            // 같은 사용자의 동시 요청. unique(user_id)가 두 번째를 막는다.
            // 이미 만들어진 계좌를 돌려주는 것이 올바른 결과다.
            return accounts.findByUserId(userId).orElseThrow(() -> e);
        }
    }
}
