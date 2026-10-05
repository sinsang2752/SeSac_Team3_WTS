package com.team.wts.user.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.wts.user.user.domain.User;
import com.team.wts.user.user.domain.UserRepository;

/**
 * 영속성 어댑터. Spring Data가 {@link UserRepository} 포트의 구현을 생성한다.
 *
 * <p>포트와 구현 사이에 별도 Adapter 클래스를 한 겹 더 두지 않았다.
 * 위임만 하는 클래스는 이득 없이 코드만 늘린다 (CLAUDE.md §52).
 */
public interface UserJpaRepository extends UserRepository, JpaRepository<User, String> {
}
