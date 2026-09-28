package com.team.wts.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 클라이언트(React WTS)의 단일 진입점.
 *
 * <p>한국투자증권 OpenAPI 호출은 이 게이트웨이를 거치지 않는다.
 * market-service가 직접 통신한다. (CLAUDE.md §0-9, §5)
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
