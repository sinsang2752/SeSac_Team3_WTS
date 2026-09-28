import { Link } from 'react-router'

import { PanelState } from '../../../components/PanelState'
import {
  directionOf,
  formatChange,
  formatPrice,
  formatQuantity,
  formatRate,
} from '../../../lib/format'
import { useStockName } from '../../market/useStockName'
import { useMarketStore } from '../../../stores/marketStore'
import { usePositions } from '../useTradingQueries'

/**
 * 보유 종목. (CLAUDE.md §28 – PositionTable)
 *
 * <p>수량·예약수량·평균단가는 서버 값이다. 평가금액과 평가손익만 실시간 현재가와 곱해
 * 여기서 낸다. 매초 바뀌는 값이라 조회로는 따라갈 수 없다.
 * 서버가 한 시점으로 계산한 값은 내 자산 화면에 있다.
 */
export function PositionTable() {
  const prices = useMarketStore((state) => state.prices)
  const { data: positions, isPending, isError } = usePositions()
  const nameOf = useStockName()

  if (isPending) return <PanelState tone="loading">불러오는 중…</PanelState>
  if (isError) {
    return (
      <PanelState tone="error" hint="잠시 후 다시 확인해 주세요.">
        보유종목을 불러오지 못했습니다.
      </PanelState>
    )
  }
  if (!positions || positions.length === 0) {
    return <PanelState hint="종목을 골라 첫 모의투자를 시작해 보세요.">아직 보유한 종목이 없어요.</PanelState>
  }

  return (
    <table className="table">
      <thead>
        <tr>
          <th scope="col">보유종목</th>
          <th scope="col">보유수량</th>
          <th scope="col">매도 예약 / 가능</th>
          <th scope="col">평균매입단가</th>
          <th scope="col">현재가</th>
          <th scope="col">평가손익</th>
        </tr>
      </thead>
      <tbody>
        {positions.map((position) => {
          const price = prices[position.symbol]?.price ?? null
          const profit = price === null ? null : (price - position.averagePrice) * position.quantity
          const rate =
            price === null || position.averagePrice === 0
              ? null
              : ((price - position.averagePrice) / position.averagePrice) * 100
          const direction = profit === null ? 'flat' : directionOf(profit)

          return (
            <tr key={position.symbol}>
              <th scope="row">
                <Link className="table__link" to={`/wts/${position.symbol}`}>
                  {nameOf(position.symbol)}
                </Link>
                <span className="table__sub numeric">{position.symbol}</span>
              </th>
              <td className="numeric">{formatQuantity(position.quantity)}</td>
              <td className="numeric">
                {position.reservedQuantity.toLocaleString('ko-KR')} /{' '}
                {formatQuantity(position.availableQuantity)}
              </td>
              <td className="numeric">{formatPrice(position.averagePrice)}원</td>
              <td className="numeric">{price === null ? '—' : `${formatPrice(price)}원`}</td>
              <td className={`numeric price--${direction}`}>
                {profit === null ? '—' : `${formatChange(profit)}원`}
                {rate !== null && <span className="table__sub">{formatRate(rate)}</span>}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
