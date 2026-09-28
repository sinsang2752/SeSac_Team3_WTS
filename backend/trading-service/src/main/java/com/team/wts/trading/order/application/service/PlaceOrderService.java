package com.team.wts.trading.order.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.common.event.KafkaTopics;
import com.team.wts.trading.account.application.service.AccountService;
import com.team.wts.trading.account.domain.VirtualAccount;
import com.team.wts.trading.config.MarketPriceProperties;
import com.team.wts.trading.execution.application.service.ExecutionService;
import com.team.wts.trading.market.domain.MarketPrice;
import com.team.wts.trading.market.domain.MarketPriceQuery;
import com.team.wts.trading.order.application.command.PlaceOrderCommand;
import com.team.wts.trading.order.domain.ExecutionStrategy;
import com.team.wts.trading.order.domain.Order;
import com.team.wts.trading.order.domain.OrderRepository;
import com.team.wts.trading.order.domain.OrderSide;
import com.team.wts.trading.order.domain.OrderType;
import com.team.wts.trading.order.event.OrderEvents;
import com.team.wts.trading.outbox.application.OutboxRecorder;
import com.team.wts.trading.position.domain.Position;
import com.team.wts.trading.position.domain.PositionRepository;

/**
 * 주문 접수. (CLAUDE.md §9.4, §9.5, §9.6, §11, §12, §14)
 *
 * <p>트랜잭션 경계는 {@link #place}뿐이다 (§52). 계좌 락 → 멱등 확인 → 검증 → 예약 →
 * 체결 판정 → 이벤트 기록까지가 한 커밋에서 끝난다. Kafka 발행은 Outbox가 따로 한다 (§15).
 *
 * <p><b>거절도 저장한다.</b> 예수금이 모자라 거절된 주문은 사용자가 주문 내역에서 이유를
 * 확인할 수 있어야 한다. 그래서 이 메서드는 거절 시 예외를 던지지 않고 REJECTED 주문을
 * 돌려준다. HTTP 에러 응답으로 바꾸는 일은 컨트롤러가 트랜잭션 밖에서 한다.
 */
@Service
public class PlaceOrderService {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

    private final AccountService accountService;
    private final OrderRepository orders;
    private final PositionRepository positions;
    private final MarketPriceQuery marketPrices;
    private final MarketPriceProperties priceProperties;
    private final ExecutionService executionService;
    private final OutboxRecorder outbox;
    private final Clock clock;
    private final Map<OrderType, ExecutionStrategy> strategies = new EnumMap<>(OrderType.class);

    public PlaceOrderService(AccountService accountService, OrderRepository orders,
            PositionRepository positions, MarketPriceQuery marketPrices,
            MarketPriceProperties priceProperties, ExecutionService executionService,
            OutboxRecorder outbox, Clock clock, List<ExecutionStrategy> executionStrategies) {
        this.accountService = accountService;
        this.orders = orders;
        this.positions = positions;
        this.marketPrices = marketPrices;
        this.priceProperties = priceProperties;
        this.executionService = executionService;
        this.outbox = outbox;
        this.clock = clock;
        for (ExecutionStrategy strategy : executionStrategies) {
            ExecutionStrategy previous = strategies.put(strategy.supports(), strategy);
            if (previous != null) {
                throw new IllegalStateException(strategy.supports() + " 체결 전략이 둘 이상이다");
            }
        }
        for (OrderType type : OrderType.values()) {
            if (!strategies.containsKey(type)) {
                throw new IllegalStateException(type + " 체결 전략이 없다");
            }
        }
    }

    @Transactional
    public Order place(PlaceOrderCommand command) {
        VirtualAccount account = accountService.getOrOpenForUpdate(command.userId());

        Optional<Order> prior = orders.findByAccountIdAndIdempotencyKey(
                account.id(), command.idempotencyKey());
        if (prior.isPresent()) {
            return replay(prior.get(), command);
        }

        Order order = Order.receive(account.id(), command.symbol(), command.side(),
                command.orderType(), command.quantity(), command.limitPrice(),
                command.idempotencyKey());

        Optional<MarketPrice> quote = marketPrices.findLatest(command.symbol());
        if (quote.isEmpty()) {
            // 상장되지 않은 종목코드이거나, market-service가 아직 이 종목의 시세를 낸 적이 없다.
            // 둘을 구분할 방법이 없으므로 같은 코드로 거절한다.
            return reject(account, order, ErrorCode.MARKET_PRICE_UNAVAILABLE);
        }
        MarketPrice price = quote.get();
        boolean stale = price.isStale(priceProperties.maxAge(), clock.instant());
        if (stale && order.orderType() == OrderType.MARKET) {
            return reject(account, order, ErrorCode.MARKET_PRICE_STALE);
        }

        return order.side() == OrderSide.BUY
                ? placeBuy(account, order, price, stale)
                : placeSell(account, order, price, stale);
    }

    /** 매수. (CLAUDE.md §9.4) */
    private Order placeBuy(VirtualAccount account, Order order, MarketPrice price, boolean stale) {
        BigDecimal reference = order.orderType() == OrderType.MARKET
                ? price.price()
                : order.limitPrice();
        BigDecimal required = reference.multiply(BigDecimal.valueOf(order.quantity()));

        if (account.availableCash().compareTo(required) < 0) {
            return reject(account, order, ErrorCode.INSUFFICIENT_BALANCE);
        }

        order.validate();
        account.reserve(required);
        order.accept();
        // 주문 ID가 있어야 이벤트를 적을 수 있다.
        Order saved = orders.save(order);
        recordCreatedAndAccepted(saved, required);

        if (executable(saved, price, stale)) {
            // 예약은 지정가 기준이라 체결 대금보다 클 수 있다. 차액은 계좌로 돌아간다.
            account.release(required);
            Position position = positions.findByAccountIdAndSymbol(account.id(), saved.symbol())
                    .orElseGet(() -> Position.open(account.id(), saved.symbol()));
            executionService.settle(account, saved, position,
                    strategies.get(saved.orderType()).execute(saved, price));
        }
        return log(account, saved);
    }

    /** 매도. (CLAUDE.md §9.5) */
    private Order placeSell(VirtualAccount account, Order order, MarketPrice price, boolean stale) {
        Position position = positions.findByAccountIdAndSymbol(account.id(), order.symbol())
                .orElseGet(() -> Position.open(account.id(), order.symbol()));

        if (position.availableQuantity() < order.quantity()) {
            return reject(account, order, ErrorCode.INSUFFICIENT_POSITION);
        }

        order.validate();
        position.reserve(order.quantity());
        order.accept();
        Order saved = orders.save(order);
        recordCreatedAndAccepted(saved, BigDecimal.ZERO);

        if (executable(saved, price, stale)) {
            position.release(saved.quantity());
            executionService.settle(account, saved, position,
                    strategies.get(saved.orderType()).execute(saved, price));
        }
        positions.save(position);
        return log(account, saved);
    }

    /**
     * 지금 체결할 수 있는가.
     *
     * <p>시세가 stale이면 지정가 주문도 체결하지 않는다. §11이 시장가만 언급하지만,
     * 낡은 가격으로 가격 조건을 판정하는 것도 같은 문제다.
     * 미체결로 두면 다음 시세 이벤트가 다시 본다 (§12).
     */
    private boolean executable(Order order, MarketPrice price, boolean stale) {
        return !stale && strategies.get(order.orderType()).canExecute(order, price);
    }

    private void recordCreatedAndAccepted(Order order, BigDecimal reservedCash) {
        String aggregateId = String.valueOf(order.id());
        outbox.record("Order", aggregateId, KafkaTopics.ORDER_CREATED,
                OrderEvents.OrderCreated.from(order));
        outbox.record("Order", aggregateId, KafkaTopics.ORDER_ACCEPTED,
                OrderEvents.OrderAccepted.of(order, reservedCash));
    }

    private Order replay(Order prior, PlaceOrderCommand command) {
        boolean same = prior.sameRequestAs(command.symbol(), command.side(), command.orderType(),
                command.quantity(), command.limitPrice());
        if (!same) {
            throw new DomainException(ErrorCode.DUPLICATE_ORDER_REQUEST);
        }
        log.info("중복 주문 요청을 기존 결과로 응답: orderId={} accountId={} status={} idempotencyKey={}",
                prior.id(), prior.accountId(), prior.status(), prior.idempotencyKey());
        return prior;
    }

    private Order reject(VirtualAccount account, Order order, ErrorCode reason) {
        order.reject(reason);
        Order saved = orders.save(order);
        outbox.record("Order", String.valueOf(saved.id()), KafkaTopics.ORDER_CREATED,
                OrderEvents.OrderCreated.from(saved));
        log.info("주문 거절: orderId={} accountId={} userId={} symbol={} side={} reason={}",
                saved.id(), account.id(), account.userId(), saved.symbol(), saved.side(), reason);
        return saved;
    }

    private Order log(VirtualAccount account, Order order) {
        log.info("주문 처리: orderId={} accountId={} userId={} symbol={} side={} type={} qty={} status={}",
                order.id(), account.id(), account.userId(), order.symbol(), order.side(),
                order.orderType(), order.quantity(), order.status());
        return order;
    }
}
