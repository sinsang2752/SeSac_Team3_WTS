package com.team.wts.trading.position.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.trading.position.adapter.in.web.dto.PositionResponse;
import com.team.wts.trading.position.application.query.PositionQueryService;

/** 포지션 API. (CLAUDE.md §24) */
@RestController
@RequestMapping("/api/trading")
public class PositionController {

    private final PositionQueryService positionQueryService;

    public PositionController(PositionQueryService positionQueryService) {
        this.positionQueryService = positionQueryService;
    }

    @GetMapping("/positions")
    public List<PositionResponse> positions(@CurrentUserId String userId) {
        return positionQueryService.findAll(userId).stream()
                .map(PositionResponse::from)
                .toList();
    }
}
