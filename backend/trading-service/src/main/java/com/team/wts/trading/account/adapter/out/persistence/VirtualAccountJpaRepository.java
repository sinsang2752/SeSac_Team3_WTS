package com.team.wts.trading.account.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.wts.trading.account.domain.VirtualAccount;
import com.team.wts.trading.account.domain.VirtualAccountRepository;

import jakarta.persistence.LockModeType;

public interface VirtualAccountJpaRepository
        extends VirtualAccountRepository, JpaRepository<VirtualAccount, Long> {

    /**
     * {@code SELECT ... FOR UPDATE}.
     *
     * <p>포트에서 상속만 하면 {@code @Lock}이 붙지 않으므로 여기서 다시 선언한다.
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from VirtualAccount a where a.userId = :userId")
    Optional<VirtualAccount> findByUserIdForUpdate(@Param("userId") String userId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from VirtualAccount a where a.id = :accountId")
    Optional<VirtualAccount> findByIdForUpdate(@Param("accountId") Long accountId);
}
