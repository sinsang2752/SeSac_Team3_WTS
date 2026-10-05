package com.team.wts.user.watchlist.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.user.watchlist.adapter.in.web.dto.WatchlistItemResponse;
import com.team.wts.user.watchlist.application.service.WatchlistService;
import com.team.wts.common.error.ApiErrorCodes;
import com.team.wts.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 관심종목 API. (CLAUDE.md §24) */
@Tag(name = "관심종목", description = "사용자별 관심종목")
@RestController
@RequestMapping("/api/users/me/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    @Operation(summary = "관심종목 목록", description = "담은 순서대로 돌려준다.")
    public List<WatchlistItemResponse> list(@CurrentUserId String userId) {
        return watchlistService.findAll(userId).stream()
                .map(WatchlistItemResponse::from)
                .toList();
    }

    /** 이미 담긴 종목이어도 성공이다. 같은 요청을 여러 번 보내도 결과가 같다. */
    @PostMapping("/{symbol}")
    @Operation(summary = "관심종목 추가", description = "이미 담긴 종목이어도 성공이다(같은 요청을 여러 번 보내도 결과가 같다). 종목이 실제로 존재하는지는 확인하지 않는다. 최대 개수(기본 50개)를 넘으면 거절한다.")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED})
    public WatchlistItemResponse add(@CurrentUserId String userId, @Parameter(description = "종목코드 (6자리)", example = "005930") @PathVariable String symbol) {
        return WatchlistItemResponse.from(watchlistService.add(userId, symbol));
    }

    @DeleteMapping("/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "관심종목 삭제", description = "담겨 있지 않은 종목을 빼도 성공이다. 본문 없이 204를 돌려준다.")
    public void remove(@CurrentUserId String userId, @Parameter(description = "종목코드 (6자리)", example = "005930") @PathVariable String symbol) {
        watchlistService.remove(userId, symbol);
    }
}
