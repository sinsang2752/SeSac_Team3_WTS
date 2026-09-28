package com.team.wts.trading.ledger.domain;

/** 원장 기록 종류. (CLAUDE.md §23) */
public enum LedgerEntryType {
    /** 계좌 개설 시 초기 가상자금 (§9.1). */
    INITIAL_DEPOSIT,
    /** 매수 체결 대금 출금. */
    BUY,
    /** 매도 체결 대금 입금. */
    SELL,
    /** 수수료·세금. MVP는 0이라 기록되지 않는다 (§44). */
    FEE,
    /** 운영상 조정. */
    ADJUSTMENT
}
