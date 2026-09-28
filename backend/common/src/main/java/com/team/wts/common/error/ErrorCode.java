package com.team.wts.common.error;

import org.springframework.http.HttpStatus;

/** 도메인 에러 코드. (CLAUDE.md §39) */
public enum ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    // ── 시세 (Phase 2) ──
    SYMBOL_NOT_FOUND(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."),
    /** 아직 시세가 한 번도 수신되지 않았다. 스트림이 준비되면 해소된다. */
    MARKET_PRICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "현재가를 조회할 수 없습니다."),

    // ── 거래 (Phase 3) ──
    /** 최신 시세가 {@code market.price.max-age}를 넘었다. 시장가 주문을 거절한다 (§11). */
    MARKET_PRICE_STALE(HttpStatus.SERVICE_UNAVAILABLE, "현재가가 오래되어 주문을 처리할 수 없습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "주문 가능한 예수금이 부족합니다."),
    INSUFFICIENT_POSITION(HttpStatus.BAD_REQUEST, "매도 가능한 수량이 부족합니다."),
    /** 이미 체결/취소된 주문을 다시 취소하려는 등, 현재 상태에서 불가능한 전이다 (§9.6). */
    INVALID_ORDER_STATE(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    /** 같은 Idempotency-Key인데 주문 내용이 다르다. 같은 내용이면 기존 주문을 돌려준다 (§14). */
    DUPLICATE_ORDER_REQUEST(HttpStatus.CONFLICT, "같은 Idempotency-Key로 다른 주문을 요청했습니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
