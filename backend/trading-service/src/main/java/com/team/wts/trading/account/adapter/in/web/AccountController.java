package com.team.wts.trading.account.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.trading.account.adapter.in.web.dto.AccountResponse;
import com.team.wts.trading.account.application.service.AccountService;

/** 계좌 API. (CLAUDE.md §24) */
@RestController
@RequestMapping("/api/trading")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/account")
    public AccountResponse account(@CurrentUserId String userId) {
        return AccountResponse.from(accountService.getOrOpen(userId));
    }
}
