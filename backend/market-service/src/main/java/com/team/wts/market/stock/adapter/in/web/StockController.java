package com.team.wts.market.stock.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.market.stock.adapter.in.web.dto.StockResponse;
import com.team.wts.market.stock.application.StockQueryService;
import com.team.wts.common.error.ApiErrorCodes;
import com.team.wts.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 종목 API. (CLAUDE.md §24) */
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
     * 종목 목록. {@code keyword}를 주면 종목코드/종목명 부분일치로 검색한다.
     * (CLAUDE.md §28 – StockSearch)
     */
    @GetMapping
    @Operation(summary = "종목 목록·검색", description = "keyword를 주면 종목코드 또는 종목명 부분일치로 검색한다. 생략하면 전체 목록이다.")
    public List<StockResponse> list(@Parameter(description = "종목코드 또는 종목명 일부. 생략하면 전체", example = "삼성") @RequestParam(required = false) String keyword) {
        return stocks.search(keyword).stream().map(StockResponse::from).toList();
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "종목 상세", description = "종목코드로 종목 하나를 조회한다.")
    @ApiErrorCodes({ErrorCode.SYMBOL_NOT_FOUND})
    public StockResponse detail(@Parameter(description = "종목코드 (6자리)", example = "005930") @PathVariable String symbol) {
        return StockResponse.from(stocks.getBySymbol(symbol));
    }
}
