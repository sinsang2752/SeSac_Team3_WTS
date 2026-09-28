package com.team.wts.trading.portfolio.application.query;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.trading.account.application.service.AccountService;
import com.team.wts.trading.account.domain.VirtualAccount;
import com.team.wts.trading.execution.domain.ExecutionRepository;
import com.team.wts.trading.market.domain.MarketPrice;
import com.team.wts.trading.market.domain.MarketPriceQuery;
import com.team.wts.trading.portfolio.adapter.in.web.dto.PortfolioResponse;
import com.team.wts.trading.position.domain.Position;
import com.team.wts.trading.position.domain.PositionRepository;

/**
 * 포트폴리오 평가. (CLAUDE.md §24, §49)
 *
 * <p>평가손익에는 현재가가 필요하다. Valkey의 최신 시세를 한 번에 읽어 계산한다 (ADR-0008).
 *
 * <p>WTS 화면의 보유종목 표는 이 API를 쓰지 않고 WebSocket 시세로 직접 계산한다.
 * 매초 바뀌는 값을 매초 조회할 수는 없기 때문이다. 이 API는 <b>한 시점의 일관된 스냅샷</b>이
 * 필요한 포트폴리오 화면을 위한 것이다.
 */
@Service
public class PortfolioQueryService {

    /** 손익률 소수 자리. */
    private static final int RATE_SCALE = 2;

    private final AccountService accountService;
    private final PositionRepository positions;
    private final ExecutionRepository executions;
    private final MarketPriceQuery marketPrices;
    private final Clock clock;

    public PortfolioQueryService(AccountService accountService, PositionRepository positions,
            ExecutionRepository executions, MarketPriceQuery marketPrices, Clock clock) {
        this.accountService = accountService;
        this.positions = positions;
        this.executions = executions;
        this.marketPrices = marketPrices;
        this.clock = clock;
    }

    /**
     * {@code readOnly = true}를 쓰지 않는다.
     *
     * <p>계좌는 최초 조회 시점에 만들어진다(ADR-0005). 읽기 전용 트랜잭션은 JDBC 커넥션을
     * read-only로 열기 때문에 그 INSERT가 실패한다. 조회지만 쓰기가 일어날 수 있는 경로다.
     */
    @Transactional
    public PortfolioResponse of(String userId) {
        VirtualAccount account = accountService.getOrOpen(userId);
        List<Position> held =
                positions.findByAccountIdAndQuantityGreaterThanOrderBySymbolAsc(account.id(), 0L);
        Map<String, MarketPrice> prices =
                marketPrices.findLatest(held.stream().map(Position::symbol).toList());

        List<PortfolioResponse.Item> items = held.stream()
                .map(position -> toItem(position, prices.get(position.symbol())))
                .toList();

        BigDecimal purchase = sum(items, PortfolioResponse.Item::purchaseAmount);
        BigDecimal evaluation = sum(items, PortfolioResponse.Item::evaluationAmount);
        BigDecimal valuationProfit = evaluation.subtract(purchase);

        return new PortfolioResponse(
                account.id(),
                account.cashBalance(),
                account.reservedCash(),
                account.availableCash(),
                purchase,
                evaluation,
                valuationProfit,
                rate(valuationProfit, purchase),
                executions.sumRealizedProfit(account.id()),
                account.cashBalance().add(evaluation),
                items,
                clock.instant());
    }

    private PortfolioResponse.Item toItem(Position position, MarketPrice price) {
        BigDecimal quantity = BigDecimal.valueOf(position.quantity());
        BigDecimal purchase = position.averagePrice().multiply(quantity);
        // 시세를 못 읽으면 매입가로 평가한다. 평가손익이 0이 될 뿐 총자산이 왜곡되지 않는다.
        BigDecimal current = price == null ? null : price.price();
        BigDecimal evaluation = (current == null ? position.averagePrice() : current).multiply(quantity);
        BigDecimal profit = evaluation.subtract(purchase);

        return new PortfolioResponse.Item(
                position.symbol(),
                position.quantity(),
                position.reservedQuantity(),
                position.availableQuantity(),
                position.averagePrice(),
                current,
                purchase,
                evaluation,
                profit,
                rate(profit, purchase));
    }

    private static BigDecimal sum(List<PortfolioResponse.Item> items,
            java.util.function.Function<PortfolioResponse.Item, BigDecimal> field) {
        return items.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 손익률(%). 분모가 0이면 0으로 둔다. */
    private static BigDecimal rate(BigDecimal profit, BigDecimal base) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return profit.multiply(BigDecimal.valueOf(100)).divide(base, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
