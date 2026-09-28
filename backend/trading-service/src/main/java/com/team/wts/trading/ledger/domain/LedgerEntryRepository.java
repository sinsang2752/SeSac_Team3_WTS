package com.team.wts.trading.ledger.domain;

import java.util.List;

/** 원장 영속화 포트. (CLAUDE.md §7) */
public interface LedgerEntryRepository {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByAccountIdOrderByIdDesc(Long accountId);
}
