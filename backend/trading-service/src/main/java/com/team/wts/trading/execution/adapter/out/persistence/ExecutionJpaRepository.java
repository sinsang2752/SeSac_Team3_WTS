package com.team.wts.trading.execution.adapter.out.persistence;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team.wts.trading.execution.domain.Execution;
import com.team.wts.trading.execution.domain.ExecutionRepository;

public interface ExecutionJpaRepository
        extends ExecutionRepository, JpaRepository<Execution, Long> {

    @Override
    @Query("""
            select coalesce(sum(e.realizedProfit), 0)
            from Execution e
            where e.accountId = :accountId
            """)
    BigDecimal sumRealizedProfit(@Param("accountId") Long accountId);
}
