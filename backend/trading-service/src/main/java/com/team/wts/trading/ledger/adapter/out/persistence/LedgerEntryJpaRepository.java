package com.team.wts.trading.ledger.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team.wts.trading.ledger.domain.LedgerEntry;
import com.team.wts.trading.ledger.domain.LedgerEntryRepository;

public interface LedgerEntryJpaRepository
        extends LedgerEntryRepository, JpaRepository<LedgerEntry, Long> {
}
