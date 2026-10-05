package com.team.wts.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * 인증된 사용자 ID를 컨트롤러 파라미터로 주입받는다. {@link CurrentUserArgumentResolver} 참고.
 *
 * <p>{@code @Parameter(hidden = true)} 는 OpenAPI 명세용이다. 이 값은 Gateway가 검증한 토큰에서
 * 나오므로 클라이언트가 보내는 파라미터가 아니다. 붙이지 않으면 springdoc이 모든 엔드포인트에
 * {@code userId} 쿼리 파라미터를 만들어낸다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface CurrentUserId {
}
