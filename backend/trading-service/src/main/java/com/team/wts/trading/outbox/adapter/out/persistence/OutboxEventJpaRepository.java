package com.team.wts.trading.outbox.adapter.out.persistence;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.team.wts.trading.outbox.domain.OutboxEvent;
import com.team.wts.trading.outbox.domain.OutboxEventRepository;
import com.team.wts.trading.outbox.domain.OutboxStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OutboxEventJpaRepository
        extends OutboxEventRepository, JpaRepository<OutboxEvent, Long> {

    @Override
    default List<OutboxEvent> claimPending(int batchSize) {
        return lockPending(OutboxStatus.PENDING, PageRequest.of(0, batchSize));
    }

    /**
     * {@code SELECT ... FOR UPDATE SKIP LOCKED}.
     *
     * <p>SKIP LOCKED가 없으면 Publisher 인스턴스가 둘일 때 하나가 다른 하나의 배치가 끝나기를
     * 기다린다. 건너뛰게 해서 서로 다른 행을 나눠 갖게 한다.
     * 잠긴 행은 다음 폴링에서 다시 대상이 된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // -2 = SKIP_LOCKED
    @Query("select e from OutboxEvent e where e.status = :status order by e.id")
    List<OutboxEvent> lockPending(@Param("status") OutboxStatus status,
            org.springframework.data.domain.Pageable pageable);
}
