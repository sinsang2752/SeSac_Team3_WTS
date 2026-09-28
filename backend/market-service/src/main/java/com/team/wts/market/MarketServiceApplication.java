package com.team.wts.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 시세 수집 · 캐시 · 이벤트 발행 서비스. (CLAUDE.md §6.2)
 *
 * <p>한국투자증권 OpenAPI와 직접 통신하는 유일한 서비스이며,
 * trading-service와는 Kafka/Valkey를 통해서만 연결된다. 직접 HTTP 호출하지 않는다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MarketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }
}
