package com.team.wts.user.user.domain;

import java.util.Optional;

/**
 * 사용자 영속화 포트. (CLAUDE.md §7 – Hexagonal)
 *
 * <p>도메인/애플리케이션 계층은 이 인터페이스만 본다.
 * 구현은 adapter/out/persistence 에 있다.
 */
public interface UserRepository {

    Optional<User> findById(String id);

    Optional<User> findByEmail(String email);

    User save(User user);
}
