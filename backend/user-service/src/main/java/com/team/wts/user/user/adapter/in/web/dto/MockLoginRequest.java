package com.team.wts.user.user.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param nickname 선택값. 비우면 이메일 앞부분을 닉네임으로 쓴다.
 */
public record MockLoginRequest(
        @NotBlank @Email @Size(max = 255)
        @Schema(description = "이메일. 소문자로 정규화한다. 최대 255자", example = "demo@wts.local")
        String email,

        @Size(max = 50)
        @Schema(description = "닉네임. 선택, 최대 50자. 비우면 이메일 앞부분을 쓴다. 기존 사용자의 닉네임은 바뀌지 않는다",
                example = "데모투자자")
        String nickname) {
}
