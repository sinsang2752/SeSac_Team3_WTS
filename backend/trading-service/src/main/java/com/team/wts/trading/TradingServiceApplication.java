package com.team.wts.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MVP 핵심 서비스. (CLAUDE.md §6.3)
 *
 * <p>order / execution / account / position / portfolio / ledger 는
 * 데이터 정합성 때문에 별도 마이크로서비스로 쪼개지 않고 이 애플리케이션 안에서 모듈로 나눈다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // Outbox Publisher 폴링 (CLAUDE.md §15)
public class TradingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingServiceApplication.class, args);
    }
}
