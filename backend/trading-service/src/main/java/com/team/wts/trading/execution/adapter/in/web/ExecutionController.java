package com.team.wts.trading.execution.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.trading.execution.adapter.in.web.dto.ExecutionResponse;
import com.team.wts.trading.execution.application.query.ExecutionQueryService;

/** 체결 내역 API. (CLAUDE.md §24) */
@RestController
@RequestMapping("/api/trading")
public class ExecutionController {

    private final ExecutionQueryService executionQueryService;

    public ExecutionController(ExecutionQueryService executionQueryService) {
        this.executionQueryService = executionQueryService;
    }

    @GetMapping("/executions")
    public List<ExecutionResponse> executions(@CurrentUserId String userId) {
        return executionQueryService.findAll(userId).stream()
                .map(ExecutionResponse::from)
                .toList();
    }
}
