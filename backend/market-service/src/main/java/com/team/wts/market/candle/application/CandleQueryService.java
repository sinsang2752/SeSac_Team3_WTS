package com.team.wts.market.candle.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.market.candle.domain.Candle;
import com.team.wts.market.candle.domain.CandleRepository;
import com.team.wts.market.stock.application.StockQueryService;

/** 1분봉 조회. (CLAUDE.md §24) */
@Service
@Transactional(readOnly = true)
public class CandleQueryService {

    private final CandleRepository candles;
    private final CandleAggregator aggregator;
    private final StockQueryService stocks;

    public CandleQueryService(CandleRepository candles,
                              CandleAggregator aggregator,
                              StockQueryService stocks) {
        this.candles = candles;
        this.aggregator = aggregator;
        this.stocks = stocks;
    }

    /**
     * 확정된 봉 + 아직 진행 중인 현재 봉.
     *
     * <p>현재 봉을 함께 주지 않으면 차트의 마지막 1분이 비어 보인다.
     *
     * @param limit 돌려줄 최대 개수. 최근 것부터 센다.
     */
    public List<Candle> recent(String symbol, int limit) {
        stocks.getBySymbol(symbol);

        // 요청 개수만큼만 조회하도록 시작 시각을 잡는다. 1분봉이므로 limit분 전부터 보면 충분하다.
        Instant from = Instant.now().minus(Duration.ofMinutes(limit)).minus(Duration.ofMinutes(1));
        List<Candle> completed =
                candles.findBySymbolAndOpenTimeGreaterThanEqualOrderByOpenTimeAsc(symbol, from);

        List<Candle> result = new ArrayList<>(completed);
        aggregator.currentCandle(symbol)
                // 이미 확정 저장된 봉과 겹치면 넣지 않는다.
                .filter(current -> completed.stream()
                        .noneMatch(candle -> candle.openTime().equals(current.openTime())))
                .ifPresent(result::add);

        return result.size() <= limit ? result : result.subList(result.size() - limit, result.size());
    }
}
