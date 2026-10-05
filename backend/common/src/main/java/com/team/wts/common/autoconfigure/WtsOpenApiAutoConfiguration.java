package com.team.wts.common.autoconfigure;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import com.team.wts.common.error.ApiErrorCodes;
import com.team.wts.common.error.ErrorCode;
import com.team.wts.common.error.ErrorResponse;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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

        OpenAPI openApi = new OpenAPI()
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
                // 기본은 인증 필요. 공개 엔드포인트는 컨트롤러에 빈 @SecurityRequirements 를 단다.
                // 공개 여부의 실제 판단은 gateway의 public-paths 다. 생성 스크립트가 둘을 대조한다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));

        // 전체 에러 코드 표. OpenAPI 표준 필드가 아니라 확장(x-)이다. 명세서 생성기가 읽는다.
        openApi.addExtension("x-error-codes", Arrays.stream(ErrorCode.values())
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", c.name());
                    row.put("status", c.status().value());
                    row.put("message", c.defaultMessage());
                    return row;
                })
                .toList());
        return openApi;
    }

    /**
     * 엔드포인트별 에러 응답을 붙인다. 목록은 {@link ApiErrorCodes}에서, 설명은 {@link ErrorCode}에서 온다.
     * 인증이 필요한 엔드포인트에는 {@code 401 UNAUTHORIZED}를 더한다.
     *
     * <p>성공 응답의 미디어 타입 {@code *}{@code /*}도 여기서 {@code application/json}으로 바로잡는다.
     * 모든 API가 JSON만 내보내는데 springdoc 기본값이 와일드카드다.
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationCustomizer wtsErrorResponseCustomizer() {
        Content errorContent = new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)));

        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            responses.values().forEach(response -> {
                Content content = response.getContent();
                if (content != null && content.containsKey("*/*")) {
                    content.addMediaType("application/json", content.remove("*/*"));
                }
            });

            Set<ErrorCode> codes = EnumSet.noneOf(ErrorCode.class);
            ApiErrorCodes declared = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
            if (declared != null) {
                codes.addAll(Arrays.asList(declared.value()));
            }
            if (!isPublic(handlerMethod)) {
                codes.add(ErrorCode.UNAUTHORIZED);
            }
            // 표 형식 명세서(md/xlsx) 생성기가 읽는다. 응답 설명 문자열을 파싱하지 않게 하기 위해서다.
            operation.addExtension("x-error-codes", codes.stream().map(Enum::name).toList());

            Map<HttpStatus, List<ErrorCode>> byStatus = codes.stream()
                    .collect(Collectors.groupingBy(ErrorCode::status, LinkedHashMap::new, Collectors.toList()));
            byStatus.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey((a, b) -> Integer.compare(a.value(), b.value())))
                    .forEach(entry -> {
                        String description = entry.getKey().getReasonPhrase() + "\n\n"
                                + entry.getValue().stream()
                                        .map(c -> "- `" + c.name() + "` — " + c.defaultMessage())
                                        .collect(Collectors.joining("\n"));
                        responses.addApiResponse(String.valueOf(entry.getKey().value()),
                                new ApiResponse().description(description).content(errorContent));
                    });
            return operation;
        };
    }

    /** 빈 {@code @SecurityRequirements}가 메서드나 컨트롤러에 있으면 공개 엔드포인트다. */
    private static boolean isPublic(org.springframework.web.method.HandlerMethod handlerMethod) {
        SecurityRequirements onMethod = handlerMethod.getMethodAnnotation(SecurityRequirements.class);
        SecurityRequirements onType = handlerMethod.getBeanType().getAnnotation(SecurityRequirements.class);
        SecurityRequirements effective = onMethod != null ? onMethod : onType;
        return effective != null && effective.value().length == 0;
    }
}
