package com.team.wts.common.autoconfigure;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.team.wts.common.error.GlobalExceptionHandler;
import com.team.wts.common.web.CurrentUserArgumentResolver;
import com.team.wts.common.web.TraceIdFilter;

import jakarta.servlet.Filter;

/**
 * servlet 기반 서비스(market / trading / user)에 공통 웹 컴포넌트를 자동 등록한다.
 *
 * <p>{@code @ConditionalOnClass}가 servlet API 존재 여부로 걸러내므로,
 * WebFlux 기반인 gateway-service에서는 이 자동설정 자체가 로드되지 않는다.
 */
@AutoConfiguration
@ConditionalOnClass(Filter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WtsCommonAutoConfiguration {

    /** traceId 필터는 다른 어떤 필터보다 먼저 돌아야 로그에 traceId가 빠지지 않는다. */
    @Bean
    @ConditionalOnMissingBean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        var registration =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public WebMvcConfigurer wtsCommonWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }
}
