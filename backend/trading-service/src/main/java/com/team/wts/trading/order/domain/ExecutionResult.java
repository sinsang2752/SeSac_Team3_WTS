package com.team.wts.trading.order.domain;

import java.math.BigDecimal;

/**
 * 체결 결과. 체결가와 체결수량이다.
 *
 * <p>Phase 3는 이 값을 계좌와 포지션에 즉시 반영하기만 하고 따로 저장하지 않는다.
 * {@code executions} 테이블과 Ledger는 Phase 4다 (CLAUDE.md §47).
 */
public record ExecutionResult(BigDecimal price, long quantity) {

    public ExecutionResult {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("체결가는 0보다 커야 한다: " + price);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("체결수량은 1 이상이어야 한다: " + quantity);
        }
    }

    /** 체결 대금. */
    public BigDecimal amount() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
