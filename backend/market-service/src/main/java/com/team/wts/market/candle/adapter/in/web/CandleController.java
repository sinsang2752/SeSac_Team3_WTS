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
import com.team.wts.common.error.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 캔들 API. (CLAUDE.md §24) */
@Tag(name = "차트", description = "분봉 조회. 인증 불필요")
@SecurityRequirements
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
    @Operation(summary = "분봉 조회", description = "최근 분봉을 openTime 오름차순으로 돌려준다. 마지막 원소는 아직 확정되지 않은 현재 봉일 수 있다. 현재 1분봉만 지원한다.")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.SYMBOL_NOT_FOUND})
    public List<CandleResponse> candles(
            @Parameter(description = "종목코드 (6자리)", example = "005930") @PathVariable String symbol,
            @Parameter(description = "봉 주기. 현재 1m만 지원", example = "1m") @RequestParam(defaultValue = SUPPORTED_INTERVAL) String interval,
            @Parameter(description = "최근 몇 개를 받을지. 1 ~ 1000", example = "120") @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {

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
