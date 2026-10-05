package com.team.wts.common.error;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 엔드포인트가 응답할 수 있는 에러 코드. OpenAPI 명세의 에러 응답이 여기서 나온다.
 *
 * <p>{@code UNAUTHORIZED}는 적지 않는다. 인증이 필요한 엔드포인트에는 자동으로 붙는다.
 * {@code INTERNAL_ERROR}도 적지 않는다. 어디서나 날 수 있고 클라이언트가 고칠 수 있는 문제가 아니다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {

    ErrorCode[] value();
}
