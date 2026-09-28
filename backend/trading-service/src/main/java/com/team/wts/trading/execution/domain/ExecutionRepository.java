package com.team.wts.trading.execution.domain;

import java.util.List;

/** 체결 영속화 포트. (CLAUDE.md §7) */
public interface ExecutionRepository {

    Execution save(Execution execution);

    List<Execution> findByAccountIdOrderByIdDesc(Long accountId);

    List<Execution> findByOrderIdOrderByIdAsc(Long orderId);

    /** 누적 실현손익. 매도 체결에만 값이 있으므로 매수는 자연히 빠진다. 없으면 0. */
    java.math.BigDecimal sumRealizedProfit(Long accountId);
}
