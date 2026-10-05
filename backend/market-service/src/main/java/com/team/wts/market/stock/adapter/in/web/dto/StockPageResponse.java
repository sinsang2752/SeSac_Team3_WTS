package com.team.wts.market.stock.adapter.in.web.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.team.wts.market.stock.domain.Stock;

import io.swagger.v3.oas.annotations.media.Schema;

/** 종목 목록 한 페이지. (CLAUDE.md §57.1) */
public record StockPageResponse(
        @Schema(description = "이 페이지의 종목") List<StockResponse> items,
        @Schema(description = "페이지 번호 (0부터)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "조건에 맞는 전체 종목 수", example = "2719") long totalElements) {

    public static StockPageResponse from(Page<Stock> page) {
        return new StockPageResponse(page.map(StockResponse::from).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** 종목코드로 찍어서 조회한 결과. 페이지가 하나뿐이다. */
    public static StockPageResponse of(List<Stock> stocks) {
        List<StockResponse> items = stocks.stream().map(StockResponse::from).toList();
        return new StockPageResponse(items, 0, items.size(), items.size());
    }
}
