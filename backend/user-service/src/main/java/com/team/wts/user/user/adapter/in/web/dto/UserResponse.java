package com.team.wts.user.user.adapter.in.web.dto;

import java.time.Instant;

import com.team.wts.user.user.domain.User;

public record UserResponse(
        String userId,
        String email,
        String nickname,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.email(), user.nickname(), user.createdAt());
    }
}
