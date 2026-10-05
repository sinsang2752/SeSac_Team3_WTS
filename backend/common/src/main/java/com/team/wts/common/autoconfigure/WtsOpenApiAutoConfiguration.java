package com.team.wts.common.autoconfigure;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import com.team.wts.common.error.ErrorCode;
import com.team.wts.common.error.ErrorResponse;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI 3 명세의 공통 부분을 채운다. springdoc이 있는 서비스에서만 등록된다
 * (WebFlux 기반 gateway-service는 springdoc 의존성이 없어 이 설정이 로드되지 않는다).
 *
 * <p>경로와 스키마는 컨트롤러와 DTO에서 자동으로 나온다. 에러 응답은 {@link ErrorCode}에서
 * 뽑는다. 명세에 손으로 적는 것은 자동으로 알아낼 수 없는 것뿐이다. 그래야 코드와 어긋나지 않는다.
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class WtsOpenApiAutoConfiguration {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI wtsOpenApi(
            @Value("${spring.application.name:wts}") String serviceName,
            @Value("${wts.openapi.gateway-url:http://localhost:8080}") String gatewayUrl) {

        Components components = new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("`POST /api/users/mock-login` 이 발급한 accessToken"));

        // 에러 응답 스키마는 ErrorResponse 레코드에서 생성한다.
        ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("WTS – " + serviceName)
                        .version("0.0.1-SNAPSHOT")
                        .description("""
                                모의투자 Web Trading System API.

                                클라이언트는 각 서비스를 직접 호출하지 않고 Gateway(%s)로만 접근한다.
                                토큰은 `POST /api/users/mock-login` 으로 발급받는다.

                                주문 API는 `Idempotency-Key` 헤더가 필수다. 같은 키로 같은 내용을
                                재요청하면 주문이 새로 생기지 않고 기존 결과가 돌아온다.

                                모든 응답에 `X-Trace-Id` 헤더가 붙는다. 에러 본문의 `traceId`와 같은 값이다.
                                """.formatted(gatewayUrl)))
                .servers(List.of(new Server().url(gatewayUrl).description("Gateway")))
                .components(components)
                // 시세 조회처럼 인증이 필요 없는 엔드포인트도 있지만, 토큰을 보내도 무시될 뿐이다.
                // 엔드포인트마다 표시하려면 컨트롤러 10개에 애너테이션을 달아야 해서 전역으로 둔다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * 모든 엔드포인트에 공통 에러 응답을 붙인다. {@link com.team.wts.common.error.GlobalExceptionHandler}가
     * 전역으로 처리하므로 어떤 엔드포인트에서도 나올 수 있다.
     *
     * <p>목록은 {@link ErrorCode}에서 만든다. 에러 코드를 추가하면 명세도 따라 바뀐다.
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationCustomizer wtsErrorResponseCustomizer() {
        Map<HttpStatus, List<ErrorCode>> byStatus = Arrays.stream(ErrorCode.values())
                .sorted(Comparator.comparingInt(c -> c.status().value()))
                .collect(Collectors.groupingBy(ErrorCode::status,
                        LinkedHashMap::new, Collectors.toList()));

        Content errorContent = new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)));

        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            byStatus.forEach((status, codes) -> {
                String key = String.valueOf(status.value());
                if (responses.containsKey(key)) {
                    return;
                }
                String description = status.getReasonPhrase() + "\n\n"
                        + codes.stream()
                                .map(c -> "- `" + c.name() + "` — " + c.defaultMessage())
                                .collect(Collectors.joining("\n"));
                responses.addApiResponse(key,
                        new ApiResponse().description(description).content(errorContent));
            });
            return operation;
        };
    }
}
