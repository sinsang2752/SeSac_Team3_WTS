package com.team.wts.trading.account.domain;

import java.util.Optional;

/** 가상 계좌 영속화 포트. (CLAUDE.md §7) */
public interface VirtualAccountRepository {

    Optional<VirtualAccount> findByUserId(String userId);

    /**
     * 계좌 행을 비관적 락으로 잡고 읽는다. (CLAUDE.md §13, ADR-0009)
     *
     * <p>잔고를 바꾸는 모든 경로는 이 메서드로 시작한다. 같은 사용자의 주문이 직렬화된다.
     */
    Optional<VirtualAccount> findByUserIdForUpdate(String userId);

    /** 사용자가 아니라 계좌 ID로 잠근다. Kafka 소비 경로에는 userId가 없다. */
    Optional<VirtualAccount> findByIdForUpdate(Long accountId);

    VirtualAccount save(VirtualAccount account);
}
