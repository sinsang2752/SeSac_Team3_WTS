package com.team.wts.trading.portfolio.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.trading.portfolio.adapter.in.web.dto.PortfolioResponse;
import com.team.wts.trading.portfolio.application.query.PortfolioQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 포트폴리오 API. (CLAUDE.md §24) */
@Tag(name = "포트폴리오", description = "현재가 기준 평가")
@RestController
@RequestMapping("/api/trading")
public class PortfolioController {

    private final PortfolioQueryService portfolioQueryService;

    public PortfolioController(PortfolioQueryService portfolioQueryService) {
        this.portfolioQueryService = portfolioQueryService;
    }

    @GetMapping("/portfolio")
    @Operation(summary = "포트폴리오 조회", description = "보유 종목을 현재가로 평가한 한 시점의 스냅샷이다. 평가손익(아직 팔지 않은 이익)과 실현손익(이미 판 이익)을 함께 준다.")
    public PortfolioResponse portfolio(@CurrentUserId String userId) {
        return portfolioQueryService.of(userId);
    }
}
