package com.team.wts.market.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시세 관련 설정. (CLAUDE.md §19, §21, §31)
 *
 * @param provider      mock | kis. 어댑터 선택 기준이다.
 * @param pinnedSymbols 항상 시세를 받는 종목 (§57.2의 고정 종목). Phase 8-1에서는 시세 공급자가
 *                      이 종목만 구독한다. 수요 기반 구독은 Phase 8-2에서 붙인다.
 * @param master        종목 마스터 동기화 (§57.1)
 * @param mock          Mock Market Mode 동작 파라미터
 * @param price         시세 유효성 판단 기준
 */
@ConfigurationProperties(prefix = "market")
public record MarketProperties(
        String provider,
        List<String> pinnedSymbols,
        Master master,
        Mock mock,
        Price price) {

    public MarketProperties {
        pinnedSymbols = pinnedSymbols == null ? List.of() : List.copyOf(pinnedSymbols);
        master = master == null ? new Master(null, null, null) : master;
        mock = mock == null ? new Mock(null, null, null, null) : mock;
        price = price == null ? new Price(null) : price;
    }

    public boolean kis() {
        return "kis".equalsIgnoreCase(provider);
    }

    /**
     * @param downloadBaseUrl 한국투자증권 종목정보 파일 위치. 파일 이름(kospi_code.mst.zip 등)을 뒤에 붙인다
     * @param refreshAfter    kis 모드에서 기동할 때 마스터가 이보다 오래됐으면 새로 받는다
     * @param downloadTimeout 연결 · 응답 대기 상한
     */
    public record Master(String downloadBaseUrl, Duration refreshAfter, Duration downloadTimeout) {
        public Master {
            downloadBaseUrl = downloadBaseUrl == null || downloadBaseUrl.isBlank()
                    ? "https://new.real.download.dws.co.kr/common/master/"
                    : downloadBaseUrl;
            refreshAfter = refreshAfter == null ? Duration.ofHours(20) : refreshAfter;
            downloadTimeout = downloadTimeout == null ? Duration.ofSeconds(30) : downloadTimeout;
        }
    }

    /**
     * @param tickInterval  시세 생성 주기
     * @param minChangeRate 한 틱당 최소 변동률 (0.001 = 0.1%)
     * @param maxChangeRate 한 틱당 최대 변동률 (0.005 = 0.5%)
     * @param seed          난수 시드. 비우면 실행마다 다른 흐름. 테스트는 고정값을 넣어 재현한다 (§31).
     */
    public record Mock(
            Duration tickInterval,
            BigDecimal minChangeRate,
            BigDecimal maxChangeRate,
            Long seed) {

        public Mock {
            tickInterval = tickInterval == null ? Duration.ofSeconds(1) : tickInterval;
            minChangeRate = minChangeRate == null ? new BigDecimal("0.001") : minChangeRate;
            maxChangeRate = maxChangeRate == null ? new BigDecimal("0.005") : maxChangeRate;
        }
    }

    /**
     * @param maxAge 이 시간을 넘긴 시세는 stale로 본다. 시장가 주문 거절 기준이다 (§11).
     */
    public record Price(Duration maxAge) {

        public Price {
            maxAge = maxAge == null ? Duration.ofSeconds(5) : maxAge;
        }
    }
}
