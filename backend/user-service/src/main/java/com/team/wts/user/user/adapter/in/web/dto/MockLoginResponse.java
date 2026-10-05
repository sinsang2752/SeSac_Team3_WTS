package com.team.wts.user.user.adapter.in.web.dto;

import com.team.wts.user.user.application.service.MockLoginService;

import io.swagger.v3.oas.annotations.media.Schema;

public record MockLoginResponse(
        @Schema(description = "사용자 ID (UUID)", example = "59407a60-bc3b-4a55-a452-72d0bcd72142")
        String userId,
        @Schema(description = "이메일 (소문자)", example = "demo@wts.local")
        String email,
        @Schema(description = "닉네임", example = "데모투자자")
        String nickname,
        @Schema(description = "인증 토큰. 이후 요청의 Authorization: Bearer 헤더에 넣는다", example = "NTk0MDdhNjAt....kVNLRyNt1D6JOW")
        String accessToken,
        @Schema(description = "토큰 유효시간 (초)", example = "86400")
        long expiresInSeconds) {

    public static MockLoginResponse from(MockLoginService.Result result) {
        return new MockLoginResponse(
                result.user().id(),
                result.user().email(),
                result.user().nickname(),
                result.accessToken(),
                result.expiresIn().toSeconds());
    }
}
