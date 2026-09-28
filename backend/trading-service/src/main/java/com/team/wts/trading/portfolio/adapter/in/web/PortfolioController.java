package com.team.wts.trading.portfolio.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.trading.portfolio.adapter.in.web.dto.PortfolioResponse;
import com.team.wts.trading.portfolio.application.query.PortfolioQueryService;

/** 포트폴리오 API. (CLAUDE.md §24) */
@RestController
@RequestMapping("/api/trading")
public class PortfolioController {

    private final PortfolioQueryService portfolioQueryService;

    public PortfolioController(PortfolioQueryService portfolioQueryService) {
        this.portfolioQueryService = portfolioQueryService;
    }

    @GetMapping("/portfolio")
    public PortfolioResponse portfolio(@CurrentUserId String userId) {
        return portfolioQueryService.of(userId);
    }
}
