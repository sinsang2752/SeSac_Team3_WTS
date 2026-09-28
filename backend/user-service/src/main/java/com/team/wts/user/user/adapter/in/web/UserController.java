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

/** 사용자 API. (CLAUDE.md §24) */
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
    public MockLoginResponse mockLogin(@Valid @RequestBody MockLoginRequest request) {
        var command = new MockLoginCommand(request.email(), request.nickname());
        return MockLoginResponse.from(mockLoginService.login(command));
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUserId String userId) {
        return UserResponse.from(userQueryService.getById(userId));
    }
}
