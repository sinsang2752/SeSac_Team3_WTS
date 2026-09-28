package com.team.wts.common.error;

import java.time.Instant;

/**
 * 모든 서비스가 공유하는 에러 응답 형식. (CLAUDE.md §39)
 *
 * <pre>
 * {
 *   "code": "INSUFFICIENT_BALANCE",
 *   "message": "주문 가능한 예수금이 부족합니다.",
 *   "traceId": "uuid",
 *   "timestamp": "..."
 * }
 * </pre>
 */
public record ErrorResponse(
        String code,
        String message,
        String traceId,
        Instant timestamp) {

    public static ErrorResponse of(ErrorCode code, String message, String traceId) {
        return new ErrorResponse(code.name(), message, traceId, Instant.now());
    }
}
