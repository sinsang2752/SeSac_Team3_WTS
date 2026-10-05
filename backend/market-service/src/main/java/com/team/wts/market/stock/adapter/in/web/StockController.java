package com.team.wts.market.stock.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.error.ApiErrorCodes;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.market.stock.adapter.in.web.dto.StockPageResponse;
import com.team.wts.market.stock.adapter.in.web.dto.StockResponse;
import com.team.wts.market.stock.application.StockQueryService;
import com.team.wts.market.stock.domain.Market;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 종목 API. (CLAUDE.md §24, §57.1) */
@Tag(name = "종목", description = "종목 마스터 조회와 검색. 인증 불필요")
@SecurityRequirements
@RestController
@RequestMapping("/api/market/stocks")
public class StockController {

    private final StockQueryService stocks;

    public StockController(StockQueryService stocks) {
        this.stocks = stocks;
    }

    /**
     * 종목 목록. 전체를 한 번에 주지 않는다 (§57.3). {@code symbols}가 있으면 그 종목만 찍어서 준다.
     */
    @GetMapping
    @Operation(summary = "종목 목록·검색",
            description = "KOSPI · KOSDAQ 상장 주권을 페이지로 돌려준다. keyword를 주면 종목코드 또는 종목명 부분일치로 "
                    + "검색한다(종목코드 일치 → 종목코드로 시작 → 이름으로 시작 순). symbols를 주면 그 종목만 돌려주고 "
                    + "다른 조건은 무시한다. 이 경우 상장폐지 종목도 포함된다(보유 종목 이름 표시용).")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED})
    public StockPageResponse list(
            @Parameter(description = "종목코드 또는 종목명 일부. 생략하면 전체 (이름순)", example = "삼성")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "시장. 생략하면 전체") @RequestParam(required = false) Market market,
            @Parameter(description = "쉼표로 구분한 종목코드. 최대 100개", example = "005930,000660")
            @RequestParam(required = false) List<String> symbols,
            @Parameter(description = "페이지 번호 (0부터)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 1 ~ 100", example = "20") @RequestParam(defaultValue = "20") int size) {
        if (symbols != null && !symbols.isEmpty()) {
            return StockPageResponse.of(stocks.findBySymbols(symbols));
        }
        return StockPageResponse.from(stocks.search(keyword, market, page, size));
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "종목 상세", description = "종목코드로 종목 하나를 조회한다. 상장폐지 종목도 돌려준다(tradable=false).")
    @ApiErrorCodes({ErrorCode.SYMBOL_NOT_FOUND})
    public StockResponse detail(
            @Parameter(description = "종목코드 (영문 대문자·숫자 6자리)", example = "005930") @PathVariable String symbol) {
        return StockResponse.from(stocks.getBySymbol(symbol));
    }
}
