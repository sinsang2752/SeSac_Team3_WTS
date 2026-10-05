package com.team.wts.common.error;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "에러 코드", example = "INSUFFICIENT_BALANCE")
        String code,
        @Schema(description = "사람이 읽을 수 있는 메시지", example = "주문 가능한 예수금이 부족합니다.")
        String message,
        @Schema(description = "추적 ID. 응답 헤더 X-Trace-Id와 같다", example = "effc7d36-ebd7-46ae-9ad8-9ea215d500b5")
        String traceId,
        @Schema(description = "발생 시각 (UTC)", example = "2026-09-22T08:12:44.101Z")
        Instant timestamp) {

    public static ErrorResponse of(ErrorCode code, String message, String traceId) {
        return new ErrorResponse(code.name(), message, traceId, Instant.now());
    }
}
