import { directionOf, formatChangeWon, formatWon } from '../../../lib/format'
import { usePortfolio } from '../../portfolio/usePortfolio'
import { useUserStore } from '../../../stores/userStore'

/**
 * 페이지 머리말의 총 자산 요약. (CLAUDE.md §28 – AccountSummary)
 *
 * <p>총 자산과 평가손익은 서버가 계산해 내려준 값을 그대로 쓴다.
 * 예수금과 평가금액을 화면에서 더하지 않는다. 계산 규칙이 두 곳에 생기면 어긋난다.
 */
export function AccountSummary() {
  const session = useUserStore((state) => state.session)
  const { data: portfolio, isPending, isError } = usePortfolio()

  if (!session) return null

  const direction = portfolio ? directionOf(portfolio.valuationProfit) : 'flat'

  return (
    <div className="account-mini">
      <span className="account-mini__label">나의 총 자산</span>
      <strong className="account-mini__value">
        {isPending ? '조회 중…' : isError || !portfolio ? '—' : formatWon(portfolio.totalAssets)}
      </strong>
      {portfolio && (
        <small className={`account-mini__profit price--${direction}`}>
          평가손익 {formatChangeWon(portfolio.valuationProfit)}
        </small>
      )}
    </div>
  )
}
