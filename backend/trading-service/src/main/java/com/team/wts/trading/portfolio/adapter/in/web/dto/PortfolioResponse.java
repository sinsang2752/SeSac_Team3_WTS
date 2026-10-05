package com.team.wts.trading.portfolio.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 포트폴리오 응답. (CLAUDE.md §24 – GET /api/trading/portfolio)
 *
 * @param totalPurchaseAmount   매입금액 합계 = Σ(평균단가 × 수량)
 * @param totalEvaluationAmount 평가금액 합계 = Σ(현재가 × 수량)
 * @param valuationProfit       평가손익 = 평가금액 - 매입금액. 아직 팔지 않은 이익이다
 * @param realizedProfit        실현손익 누적. 이미 팔아서 확정된 이익이다
 * @param totalAssets           총 자산 = 예수금 + 평가금액
 */
import io.swagger.v3.oas.annotations.media.Schema;

public record PortfolioResponse(
        @Schema(description = "가상 계좌 ID", example = "1")
        Long accountId,
        @Schema(description = "예수금 (원)", example = "98831200")
        BigDecimal cashBalance,
        @Schema(description = "미체결 매수 주문이 묶어둔 금액 (원)", example = "0")
        BigDecimal reservedCash,
        @Schema(description = "주문 가능 금액 (원) = cashBalance - reservedCash", example = "98831200")
        BigDecimal availableCash,
        @Schema(description = "매입금액 합계 (원) = Σ(평균단가 × 수량)", example = "1168800")
        BigDecimal totalPurchaseAmount,
        @Schema(description = "평가금액 합계 (원) = Σ(현재가 × 수량)", example = "1169000")
        BigDecimal totalEvaluationAmount,
        @Schema(description = "평가손익 (원) = 평가금액 - 매입금액. 아직 팔지 않은 이익", example = "200")
        BigDecimal valuationProfit,
        @Schema(description = "평가수익률 (%)", example = "0.02")
        BigDecimal valuationProfitRate,
        @Schema(description = "실현손익 누적 (원). 이미 팔아서 확정된 이익", example = "1600")
        BigDecimal realizedProfit,
        @Schema(description = "총자산 (원) = 예수금 + 평가금액", example = "100000200")
        BigDecimal totalAssets,
        @Schema(description = "보유 종목별 평가")
        List<Item> positions,
        @Schema(description = "평가 시각 (UTC). 이 시점의 스냅샷이다", example = "2026-09-22T08:12:44.101Z")
        Instant evaluatedAt) {

    /**
     * @param currentPrice 시세를 읽지 못하면 null이다. 이때 평가금액은 매입금액과 같게 둔다
     *                     (평가손익 0). 모르는 값을 0원으로 만들어 총자산을 왜곡하지 않기 위해서다.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "PortfolioItem")
    public record Item(
            @Schema(description = "종목코드", example = "005930")
            String symbol,
            @Schema(description = "보유 수량 (주)", example = "10")
            long quantity,
            @Schema(description = "미체결 매도 주문이 묶어둔 수량 (주)", example = "0")
            long reservedQuantity,
            @Schema(description = "매도 가능 수량 (주)", example = "10")
            long availableQuantity,
            @Schema(description = "평균 매입단가 (원)", example = "80000")
            BigDecimal averagePrice,
            @Schema(description = "현재가 (원). 시세를 읽지 못하면 null이고, 이때 평가금액은 매입금액과 같게 둔다", example = "80400")
            BigDecimal currentPrice,
            @Schema(description = "매입금액 (원) = 평균단가 × 수량", example = "800000")
            BigDecimal purchaseAmount,
            @Schema(description = "평가금액 (원) = 현재가 × 수량", example = "804000")
            BigDecimal evaluationAmount,
            @Schema(description = "평가손익 (원)", example = "4000")
            BigDecimal valuationProfit,
            @Schema(description = "평가수익률 (%)", example = "0.50")
            BigDecimal valuationProfitRate) {
    }
}
