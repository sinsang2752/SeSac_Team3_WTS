package com.team.wts.trading.execution.application.service;

import java.math.BigDecimal;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.common.event.KafkaTopics;
import com.team.wts.trading.account.domain.VirtualAccount;
import com.team.wts.trading.execution.domain.Execution;
import com.team.wts.trading.execution.domain.ExecutionRepository;
import com.team.wts.trading.execution.domain.TradingFeeCalculator;
import com.team.wts.trading.execution.event.ExecutionCompleted;
import com.team.wts.trading.ledger.application.service.LedgerService;
import com.team.wts.trading.ledger.domain.LedgerEntryType;
import com.team.wts.trading.order.domain.ExecutionResult;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.outbox.application.OutboxRecorder;
import com.team.wts.trading.position.domain.Position;
import com.team.wts.trading.position.domain.PositionRepository;
import com.team.wts.trading.position.event.PositionChanged;

/**
 * 체결을 계좌·포지션·원장에 반영한다. (CLAUDE.md §10, §23)
 *
 * <p>주문 접수 시점의 즉시 체결과 시세 이벤트로 깨어난 미체결 지정가 체결이 <b>같은 경로</b>를
 * 쓴다. 두 벌로 나뉘면 한쪽만 고치는 일이 반드시 생긴다.
 *
 * <p>호출 전제:
 * <ul>
 *   <li>계좌 행이 비관적 락으로 잠겨 있다 (ADR-0009)
 *   <li>이 주문이 묶어둔 예수금/수량이 이미 해제돼 있다
 * </ul>
 * 예약 해제를 여기서 하지 않는 이유는, 해제할 금액을 아는 쪽이 호출자이기 때문이다.
 * 시장가는 접수 시점에 계산한 금액이고 지정가는 {@code limitPrice × 미체결수량}이다.
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionRepository executions;
    private final PositionRepository positions;
    private final LedgerService ledger;
    private final TradingFeeCalculator feeCalculator;
    private final OutboxRecorder outbox;
    private final Clock clock;

    public ExecutionService(ExecutionRepository executions, PositionRepository positions,
            LedgerService ledger, TradingFeeCalculator feeCalculator, OutboxRecorder outbox,
            Clock clock) {
        this.executions = executions;
        this.positions = positions;
        this.ledger = ledger;
        this.feeCalculator = feeCalculator;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Execution settle(VirtualAccount account, Order order, Position position,
            ExecutionResult result) {

        BigDecimal amount = result.amount();
        BigDecimal fee = feeCalculator.calculate(order.side(), amount);
        BigDecimal realizedProfit =
                order.side() == OrderSide.SELL
                        // 평균단가가 바뀌기 전에 계산한다.
                        ? position.realizedProfitOf(result.quantity(), result.price())
                        : null;

        BigDecimal balanceBefore = account.cashBalance();
        LedgerEntryType ledgerType;

        if (order.side() == OrderSide.BUY) {
            account.withdraw(amount);
            position.add(result.quantity(), result.price());
            ledgerType = LedgerEntryType.BUY;
        } else {
            position.reduce(result.quantity());
            account.deposit(amount);
            ledgerType = LedgerEntryType.SELL;
        }
        order.fill(result.quantity());
        positions.save(position);

        Execution execution = executions.save(Execution.of(order.id(), account.id(), order.symbol(),
                order.side(), result.price(), result.quantity(), fee, realizedProfit,
                clock.instant()));

        BigDecimal signedAmount = order.side() == OrderSide.BUY ? amount.negate() : amount;
        ledger.record(account, order.id(), execution.id(), ledgerType, signedAmount, balanceBefore);
        chargeFee(account, order, execution, fee);

        outbox.record("Execution", String.valueOf(execution.id()), KafkaTopics.EXECUTION_COMPLETED,
                ExecutionCompleted.from(execution));
        outbox.record("Position", account.id() + ":" + position.symbol(),
                KafkaTopics.POSITION_CHANGED, PositionChanged.from(position));

        log.info("체결: executionId={} orderId={} accountId={} symbol={} side={} price={} qty={}"
                        + " realizedProfit={}",
                execution.id(), order.id(), account.id(), order.symbol(), order.side(),
                result.price(), result.quantity(), realizedProfit);
        return execution;
    }

    /** MVP는 요율이 0이라 아무 일도 하지 않는다 (CLAUDE.md §44). */
    private void chargeFee(VirtualAccount account, Order order, Execution execution,
            BigDecimal fee) {
        if (fee.signum() <= 0) {
            return;
        }
        BigDecimal before = account.cashBalance();
        account.withdraw(fee);
        ledger.record(account, order.id(), execution.id(), LedgerEntryType.FEE, fee.negate(), before);
    }
}
