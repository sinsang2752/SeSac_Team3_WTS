package com.team.wts.trading.position.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.trading.position.adapter.in.web.dto.PositionResponse;
import com.team.wts.trading.position.application.query.PositionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 포지션 API. (CLAUDE.md §24) */
@Tag(name = "보유종목", description = "종목별 보유 수량과 평균단가")
@RestController
@RequestMapping("/api/trading")
public class PositionController {

    private final PositionQueryService positionQueryService;

    public PositionController(PositionQueryService positionQueryService) {
        this.positionQueryService = positionQueryService;
    }

    @GetMapping("/positions")
    @Operation(summary = "보유종목 조회", description = "보유 중인 종목만 돌려준다. 전량 매도한 종목은 빠진다. 평가금액·평가손익은 포트폴리오 조회에 있다.")
    public List<PositionResponse> positions(@CurrentUserId String userId) {
        return positionQueryService.findAll(userId).stream()
                .map(PositionResponse::from)
                .toList();
    }
}
