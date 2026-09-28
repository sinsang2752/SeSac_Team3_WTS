package com.team.wts.market.candle.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.market.candle.adapter.in.web.dto.CandleResponse;
import com.team.wts.market.candle.application.CandleQueryService;

/** 캔들 API. (CLAUDE.md §24) */
@RestController
@RequestMapping("/api/market/stocks/{symbol}/candles")
public class CandleController {

    /** MVP는 1분봉만 만든다. 다른 주기는 이후 단계에서 추가한다. */
    private static final String SUPPORTED_INTERVAL = "1m";
    private static final int DEFAULT_LIMIT = 120;
    private static final int MAX_LIMIT = 1000;

    private final CandleQueryService candles;

    public CandleController(CandleQueryService candles) {
        this.candles = candles;
    }

    @GetMapping
    public List<CandleResponse> candles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = SUPPORTED_INTERVAL) String interval,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {

        if (!SUPPORTED_INTERVAL.equals(interval)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "지원하지 않는 interval 입니다: " + interval + " (사용 가능: " + SUPPORTED_INTERVAL + ")");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "limit 은 1 이상 " + MAX_LIMIT + " 이하여야 합니다: " + limit);
        }

        return candles.recent(symbol, limit).stream().map(CandleResponse::from).toList();
    }
}
