package com.team.wts.market.support;

import java.math.BigDecimal;

/** 응답에 싣는 금액의 소수 자릿수를 맞춘다. */
public final class Decimals {

    private Decimals() {
    }

    /**
     * 최소 표현으로 바꾼다. DB에서 읽은 DECIMAL(19,4)는 80300.0000으로, 메모리 값은 80300으로 나가
     * 같은 응답 안에서 형식이 갈리는 것을 막는다. null은 그대로 둔다.
     */
    public static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        // stripTrailingZeros는 80300을 8.03E+4(scale -2)로 만들 수 있다. 지수 표기를 피한다.
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
