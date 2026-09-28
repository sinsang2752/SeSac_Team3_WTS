package com.team.wts.common.error;

/**
 * 도메인 규칙 위반. HTTP 상태와 응답 코드는 {@link ErrorCode}가 정한다.
 *
 * <p>스택 트레이스를 남기지 않는다. 예상된 흐름이지 장애가 아니기 때문이다.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public DomainException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public DomainException(ErrorCode errorCode, String message) {
        super(message, null, false, false);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
