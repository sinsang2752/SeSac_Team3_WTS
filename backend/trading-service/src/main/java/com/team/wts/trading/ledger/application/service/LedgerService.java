package com.team.wts.trading.ledger.application.service;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.common.event.KafkaTopics;
import com.team.wts.trading.account.domain.VirtualAccount;
import com.team.wts.trading.account.event.AccountBalanceChanged;
import com.team.wts.trading.ledger.domain.LedgerEntry;
import com.team.wts.trading.ledger.domain.LedgerEntryRepository;
import com.team.wts.trading.ledger.domain.LedgerEntryType;
import com.team.wts.trading.ledger.event.LedgerCreated;
import com.team.wts.trading.outbox.application.OutboxRecorder;

/**
 * 예수금 변동을 원장에 남긴다. (CLAUDE.md §23)
 *
 * <p>잔고를 바꾼 <b>직후</b>에 불러야 한다. {@code account.cashBalance()}를 변경 후 값으로 읽고,
 * 변경 전 값은 호출자가 넘긴다. 이 순서가 어긋나면 원장이 잔고를 설명하지 못한다.
 *
 * <p>원장 기록과 {@code account.balance.changed}는 같은 사건의 두 표현이라 여기서 함께 낸다.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository ledger;
    private final OutboxRecorder outbox;

    public LedgerService(LedgerEntryRepository ledger, OutboxRecorder outbox) {
        this.ledger = ledger;
        this.outbox = outbox;
    }

    /**
     * @param amount        부호 있는 금액. 입금은 양수, 출금은 음수
     * @param beforeBalance 잔고를 바꾸기 전 값
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerEntry record(VirtualAccount account, Long orderId, Long executionId,
            LedgerEntryType type, BigDecimal amount, BigDecimal beforeBalance) {

        LedgerEntry entry = ledger.save(LedgerEntry.of(account.id(), orderId, executionId, type,
                amount, beforeBalance, account.cashBalance()));

        outbox.record("Ledger", String.valueOf(account.id()), KafkaTopics.LEDGER_CREATED,
                LedgerCreated.from(entry));
        outbox.record("Account", String.valueOf(account.id()), KafkaTopics.ACCOUNT_BALANCE_CHANGED,
                new AccountBalanceChanged(account.id(), account.userId(), type, amount,
                        beforeBalance, account.cashBalance(), account.reservedCash(),
                        account.availableCash()));

        log.info("원장 기록: ledgerId={} accountId={} userId={} type={} amount={} after={}",
                entry.id(), account.id(), account.userId(), type, amount, account.cashBalance());
        return entry;
    }
}
