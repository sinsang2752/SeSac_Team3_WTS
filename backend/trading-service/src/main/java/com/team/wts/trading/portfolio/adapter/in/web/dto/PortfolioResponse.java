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
        Long accountId,
        BigDecimal cashBalance,
        BigDecimal reservedCash,
        BigDecimal availableCash,
        BigDecimal totalPurchaseAmount,
        BigDecimal totalEvaluationAmount,
        BigDecimal valuationProfit,
        BigDecimal valuationProfitRate,
        BigDecimal realizedProfit,
        BigDecimal totalAssets,
        List<Item> positions,
        Instant evaluatedAt) {

    /**
     * @param currentPrice 시세를 읽지 못하면 null이다. 이때 평가금액은 매입금액과 같게 둔다
     *                     (평가손익 0). 모르는 값을 0원으로 만들어 총자산을 왜곡하지 않기 위해서다.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "PortfolioItem")
    public record Item(
            String symbol,
            long quantity,
            long reservedQuantity,
            long availableQuantity,
            BigDecimal averagePrice,
            BigDecimal currentPrice,
            BigDecimal purchaseAmount,
            BigDecimal evaluationAmount,
            BigDecimal valuationProfit,
            BigDecimal valuationProfitRate) {
    }
}
