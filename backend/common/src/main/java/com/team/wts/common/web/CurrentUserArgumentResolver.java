package com.team.wts.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.team.wts.common.error.DomainException;
import com.team.wts.common.error.ErrorCode;

/**
 * 컨트롤러 파라미터에 {@link CurrentUserId}를 붙이면 Gateway가 심어준 사용자 ID가 주입된다.
 *
 * <p>헤더가 없으면 인증되지 않은 요청이므로 401로 끊는다.
 * 컨트롤러마다 헤더를 꺼내 검사하는 코드를 반복하지 않기 위한 장치다.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(WtsHeaders.USER_ID);
        if (userId == null || userId.isBlank()) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
