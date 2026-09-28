package com.team.wts.user.user.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param nickname 선택값. 비우면 이메일 앞부분을 닉네임으로 쓴다.
 */
public record MockLoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 50) String nickname) {
}
