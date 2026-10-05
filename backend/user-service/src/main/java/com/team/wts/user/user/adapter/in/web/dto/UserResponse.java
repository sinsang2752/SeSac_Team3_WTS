package com.team.wts.user.user.adapter.in.web.dto;

import java.time.Instant;

import com.team.wts.user.user.domain.User;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        @Schema(description = "사용자 ID (UUID)", example = "59407a60-bc3b-4a55-a452-72d0bcd72142")
        String userId,
        @Schema(description = "이메일 (소문자)", example = "demo@wts.local")
        String email,
        @Schema(description = "닉네임", example = "데모투자자")
        String nickname,
        @Schema(description = "가입 시각 (UTC)", example = "2026-09-22T08:12:44.101Z")
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.email(), user.nickname(), user.createdAt());
    }
}
