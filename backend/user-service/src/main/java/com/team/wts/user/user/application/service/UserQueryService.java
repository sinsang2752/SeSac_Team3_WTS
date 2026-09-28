package com.team.wts.user.user.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.user.user.domain.User;
import com.team.wts.user.user.domain.UserRepository;

/**
 * 사용자 조회. (CLAUDE.md §45 – Query 측)
 *
 * <p>인자가 userId 하나뿐이라 별도 Query 객체를 만들지 않았다.
 * 조건이 여러 개인 조회가 생기면 그때 query 패키지에 객체를 둔다 (§52).
 */
@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository users;

    public UserQueryService(UserRepository users) {
        this.users = users;
    }

    public User getById(String userId) {
        return users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
    }
}
