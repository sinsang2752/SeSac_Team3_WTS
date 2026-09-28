import { PanelState } from '../../../components/PanelState'
import {
  directionOf,
  formatChange,
  formatPrice,
  formatQuantity,
  formatSeoulDateTime,
} from '../../../lib/format'
import { useStockName } from '../../market/useStockName'
import { useExecutions } from '../useTradingQueries'

/**
 * 체결 내역. (CLAUDE.md §28, ui-requirements §8)
 *
 * <p>주문 내역과 다르다. 주문은 "얼마에 사겠다"이고, 여기는 "얼마에 샀다"이다.
 * 시장가 주문의 체결가는 여기에만 남는다.
 */
export function ExecutionHistory() {
  const { data: executions, isPending, isError } = useExecutions()
  const nameOf = useStockName()

  if (isPending) return <PanelState tone="loading">불러오는 중…</PanelState>
  if (isError) {
    return (
      <PanelState tone="error" hint="잠시 후 다시 확인해 주세요.">
        체결내역을 불러오지 못했습니다.
      </PanelState>
    )
  }
  if (!executions || executions.length === 0) {
    return <PanelState hint="주문이 체결되면 여기에 기록됩니다.">체결내역이 없습니다.</PanelState>
  }

  return (
    <table className="table">
      <thead>
        <tr>
          <th scope="col">종목 · 체결시각</th>
          <th scope="col">구분</th>
          <th scope="col">체결가격</th>
          <th scope="col">체결수량</th>
          <th scope="col">체결금액</th>
          <th scope="col">실현손익</th>
        </tr>
      </thead>
      <tbody>
        {executions.map((execution) => (
          <tr key={execution.executionId}>
            <th scope="row">
              <span className="table__name">{nameOf(execution.symbol)}</span>
              <span className="table__sub numeric">{formatSeoulDateTime(execution.executedAt)}</span>
            </th>
            <td className={execution.side === 'BUY' ? 'price--up' : 'price--down'}>
              {execution.side === 'BUY' ? '매수' : '매도'}
            </td>
            <td className="numeric">{formatPrice(execution.price)}원</td>
            <td className="numeric">{formatQuantity(execution.quantity)}</td>
            <td className="numeric">{formatPrice(execution.amount)}원</td>
            <td
              className={`numeric${
                execution.realizedProfit === undefined
                  ? ''
                  : ` price--${directionOf(execution.realizedProfit)}`
              }`}
            >
              {/* 매수 체결에는 실현손익이 없다. 0원과 구분해야 한다. */}
              {execution.realizedProfit === undefined
                ? '—'
                : `${formatChange(execution.realizedProfit)}원`}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
