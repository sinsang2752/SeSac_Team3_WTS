package com.team.wts.trading.account.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.trading.account.adapter.in.web.dto.AccountResponse;
import com.team.wts.trading.account.application.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 계좌 API. (CLAUDE.md §24) */
@Tag(name = "계좌", description = "가상 계좌")
@RestController
@RequestMapping("/api/trading")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/account")
    @Operation(summary = "계좌 조회", description = "예수금·예약금·주문 가능 금액을 돌려준다. 계좌가 없으면 초기 가상자금(기본 1억원)으로 개설한다.")
    public AccountResponse account(@CurrentUserId String userId) {
        return AccountResponse.from(accountService.getOrOpen(userId));
    }
}
