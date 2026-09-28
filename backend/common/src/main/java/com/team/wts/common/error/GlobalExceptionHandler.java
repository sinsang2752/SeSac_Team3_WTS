package com.team.wts.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.team.wts.common.web.TraceIdFilter;

/** 모든 servlet 서비스가 공유하는 에러 응답 변환기. (CLAUDE.md §39) */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException e) {
        // 도메인 규칙 위반은 장애가 아니다. 스택 트레이스 없이 한 줄만 남긴다.
        log.info("도메인 규칙 위반: code={} message={}", e.errorCode(), e.getMessage());
        return respond(e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse(ErrorCode.VALIDATION_FAILED.defaultMessage());
        return respond(ErrorCode.VALIDATION_FAILED, detail);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return respond(ErrorCode.VALIDATION_FAILED, e.getHeaderName() + " 헤더가 필요합니다.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        // 잘못된 JSON, 정의되지 않은 enum 값 등. 원인 메시지에 내부 타입명이 섞이므로 노출하지 않는다.
        log.info("요청 본문을 읽을 수 없음: {}", e.getMessage());
        return respond(ErrorCode.VALIDATION_FAILED, "요청 본문 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 예상 못 한 예외는 반드시 스택 트레이스를 남긴다 (CLAUDE.md §52 – 예외를 무시하지 말 것).
        log.error("처리하지 못한 예외", e);
        // 내부 메시지를 클라이언트에 노출하지 않는다.
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, message, traceId));
    }
}
