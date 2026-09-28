package com.team.wts.trading.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각 기준. 저장은 UTC다 (CLAUDE.md §43).
 *
 * <p>Bean으로 두는 이유는 시세 stale 판정(§11)을 테스트에서 고정 시각으로 검증하기 위해서다.
 */
@Configuration
public class TradingClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
