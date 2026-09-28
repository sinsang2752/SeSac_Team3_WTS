package com.team.wts.trading.outbox.domain;

import java.util.List;

/** Outbox 영속화 포트. (CLAUDE.md §7) */
public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    /**
     * 발행 대기 이벤트를 id 순서로 잡는다. 다른 인스턴스가 잡은 행은 건너뛴다.
     *
     * @param batchSize 한 번에 가져올 최대 건수
     */
    List<OutboxEvent> claimPending(int batchSize);
}
