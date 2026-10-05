package com.team.wts.common.market;

import java.util.regex.Pattern;

/**
 * 종목코드(단축코드) 형식. 서비스들이 같은 규칙으로 검증한다.
 *
 * <p>숫자 6자리만이 아니다. 2024년부터 신규 상장 종목에 영문 대문자가 섞인 코드가 붙는다
 * (예: {@code 0001A0}, 우선주 {@code 00088K}). 2026-10 기준 KOSPI·KOSDAQ 주권 2,719개 중 85개다.
 * 숫자만 받으면 이 종목들은 주문도 관심종목 추가도 못 한다.
 *
 * <p>주권 기준이다. ETN은 7자리(예: {@code Q500067})라 상품을 넓힐 때(CLAUDE.md §63 Phase 13) 다시 정한다.
 */
public final class StockSymbol {

    /** Bean Validation {@code @Pattern}에 쓰려면 컴파일 타임 상수여야 한다. */
    public static final String REGEX = "[0-9A-Z]{6}";

    public static final String FORMAT_MESSAGE = "종목코드는 영문 대문자·숫자 6자리입니다.";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private StockSymbol() {
    }

    public static boolean isValid(String symbol) {
        return symbol != null && PATTERN.matcher(symbol).matches();
    }
}
