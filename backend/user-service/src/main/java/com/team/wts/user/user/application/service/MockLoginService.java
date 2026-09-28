package com.team.wts.user.user.application.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.common.auth.AuthProperties;
import com.team.wts.common.auth.MockAuthToken;
import com.team.wts.user.user.application.command.MockLoginCommand;
import com.team.wts.user.user.domain.User;
import com.team.wts.user.user.domain.UserRepository;

/**
 * Mock 로그인. (CLAUDE.md §6.4 – 인증 우선순위 1순위, §24)
 *
 * <p>비밀번호가 없다. 이메일만으로 사용자를 찾거나 만들고 토큰을 발급한다.
 * 실제 인증은 Phase 8에서 Amazon Cognito로 교체한다 (§48).
 */
@Service
public class MockLoginService {

    private static final Logger log = LoggerFactory.getLogger(MockLoginService.class);

    private final UserRepository users;
    private final MockAuthToken tokens;
    private final Duration tokenTtl;

    public MockLoginService(UserRepository users, MockAuthToken tokens, AuthProperties authProperties) {
        this.users = users;
        this.tokens = tokens;
        this.tokenTtl = authProperties.tokenTtl();
    }

    @Transactional
    public Result login(MockLoginCommand command) {
        String email = normalizeEmail(command.email());
        User user = users.findByEmail(email)
                .orElseGet(() -> register(email, command.nickname()));

        // 비밀번호를 로그에 남기지 않는 것과 같은 이유로 토큰도 남기지 않는다 (CLAUDE.md §40).
        log.info("Mock 로그인: userId={} email={}", user.id(), email);

        return new Result(user, tokens.issue(user.id(), tokenTtl), tokenTtl);
    }

    private User register(String email, String nickname) {
        User candidate = User.register(email, resolveNickname(email, nickname));
        try {
            return users.save(candidate);
        } catch (DataIntegrityViolationException e) {
            // 같은 이메일로 동시에 로그인하면 unique 제약에 걸린다.
            // 먼저 성공한 쪽의 사용자를 돌려주는 것이 올바른 결과다.
            return users.findByEmail(email).orElseThrow(() -> e);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String resolveNickname(String email, String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /** 로그인 결과. 토큰 발급 정보까지 함께 돌려준다. */
    public record Result(User user, String accessToken, Duration expiresIn) {
    }
}
