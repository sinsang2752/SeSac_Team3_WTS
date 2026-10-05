package com.team.wts.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("파라미터 타입 변환 실패는 500이 아니라 400 VALIDATION_FAILED다")
    void typeMismatchIsClientError() throws NoSuchMethodException {
        var parameter = new MethodParameter(
                Sample.class.getDeclaredMethod("find", Long.class), 0);
        var e = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "orderId", parameter, new NumberFormatException());

        var response = handler.handleTypeMismatch(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().message()).contains("orderId").contains("abc");
    }

    @SuppressWarnings("unused")
    private static class Sample {
        void find(Long orderId) {
        }
    }
}
