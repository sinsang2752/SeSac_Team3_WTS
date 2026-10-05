package com.team.wts.user.user.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.wts.common.web.CurrentUserId;
import com.team.wts.user.user.adapter.in.web.dto.MockLoginRequest;
import com.team.wts.user.user.adapter.in.web.dto.MockLoginResponse;
import com.team.wts.user.user.adapter.in.web.dto.UserResponse;
import com.team.wts.user.user.application.command.MockLoginCommand;
import com.team.wts.user.user.application.service.MockLoginService;
import com.team.wts.user.user.application.service.UserQueryService;

import jakarta.validation.Valid;
import com.team.wts.common.error.ApiErrorCodes;
import com.team.wts.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 사용자 API. (CLAUDE.md §24) */
@Tag(name = "사용자", description = "모의 로그인과 내 정보")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final MockLoginService mockLoginService;
    private final UserQueryService userQueryService;

    public UserController(MockLoginService mockLoginService, UserQueryService userQueryService) {
        this.mockLoginService = mockLoginService;
        this.userQueryService = userQueryService;
    }

    /** 인증 없이 접근 가능한 유일한 엔드포인트다 (gateway의 public-paths 참고). */
    @PostMapping("/mock-login")
    @Operation(summary = "모의 로그인", description = "이메일로 사용자를 찾고, 없으면 만든 뒤 토큰을 발급한다. 이메일은 소문자로 정규화하므로 대소문자가 달라도 같은 사용자다. 인증 없이 호출한다.")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED})
    @SecurityRequirements
    public MockLoginResponse mockLogin(@Valid @RequestBody MockLoginRequest request) {
        var command = new MockLoginCommand(request.email(), request.nickname());
        return MockLoginResponse.from(mockLoginService.login(command));
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "토큰의 사용자 정보를 돌려준다.")
    @ApiErrorCodes({ErrorCode.USER_NOT_FOUND})
    public UserResponse me(@CurrentUserId String userId) {
        return UserResponse.from(userQueryService.getById(userId));
    }
}
