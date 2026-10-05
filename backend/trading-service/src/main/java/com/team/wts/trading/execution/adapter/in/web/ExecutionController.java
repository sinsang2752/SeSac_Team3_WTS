package com.team.wts.trading.execution.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.trading.execution.adapter.in.web.dto.ExecutionResponse;
import com.team.wts.trading.execution.application.query.ExecutionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 체결 내역 API. (CLAUDE.md §24) */
@Tag(name = "체결", description = "체결 내역")
@RestController
@RequestMapping("/api/trading")
public class ExecutionController {

    private final ExecutionQueryService executionQueryService;

    public ExecutionController(ExecutionQueryService executionQueryService) {
        this.executionQueryService = executionQueryService;
    }

    @GetMapping("/executions")
    @Operation(summary = "체결 내역 조회", description = "최신순으로 돌려준다. 주문의 filledQuantity만으로는 얼마에 체결됐는지 알 수 없다. 체결가는 여기에만 있다.")
    public List<ExecutionResponse> executions(@CurrentUserId String userId) {
        return executionQueryService.findAll(userId).stream()
                .map(ExecutionResponse::from)
                .toList();
    }
}
