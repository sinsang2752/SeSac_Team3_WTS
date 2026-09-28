package com.team.wts.user.user.adapter.in.web.dto;

import com.team.wts.user.user.application.service.MockLoginService;

public record MockLoginResponse(
        String userId,
        String email,
        String nickname,
        String accessToken,
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
