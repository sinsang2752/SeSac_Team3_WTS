import { PanelState } from '../../../components/PanelState'
import { formatPrice, formatQuantity, formatSeoulDateTime } from '../../../lib/format'
import { useStockNames } from '../../market/useStockNames'
import { useOrderHistory } from '../useTradingQueries'
import { rejectReasonLabel } from '../orderStatus'
import { OrderStatusBadge } from './OrderStatusBadge'

/**
 * 주문 내역. 거절된 주문도 사유와 함께 남는다. (ui-requirements §8)
 *
 * <p>주문 정정은 아직 없다. 동작하지 않는 버튼을 두지 않는다.
 */
export function OrderHistory() {
  const { data: orders, isPending, isError } = useOrderHistory()
  const nameOf = useStockNames((orders ?? []).map((item) => item.symbol))

  if (isPending) return <PanelState tone="loading">불러오는 중…</PanelState>
  if (isError) {
    return (
      <PanelState tone="error" hint="잠시 후 다시 확인해 주세요.">
        주문내역을 불러오지 못했습니다.
      </PanelState>
    )
  }
  if (!orders || orders.length === 0) {
    return <PanelState hint="첫 모의투자를 시작해 보세요.">주문내역이 없습니다.</PanelState>
  }

  return (
    <table className="table">
      <thead>
        <tr>
          <th scope="col">종목 · 주문시각</th>
          <th scope="col">구분</th>
          <th scope="col">주문유형</th>
          <th scope="col">주문가격</th>
          <th scope="col">주문수량</th>
          <th scope="col">체결수량</th>
          <th scope="col">미체결수량</th>
          <th scope="col">상태</th>
        </tr>
      </thead>
      <tbody>
        {orders.map((order) => (
          <tr key={order.orderId}>
            <th scope="row">
              <span className="table__name">{nameOf(order.symbol)}</span>
              <span className="table__sub numeric">{formatSeoulDateTime(order.createdAt)}</span>
            </th>
            <td className={order.side === 'BUY' ? 'price--up' : 'price--down'}>
              {order.side === 'BUY' ? '매수' : '매도'}
            </td>
            <td>{order.orderType === 'MARKET' ? '시장가' : '지정가'}</td>
            <td className="numeric">
              {order.limitPrice === undefined ? '시장가' : `${formatPrice(order.limitPrice)}원`}
            </td>
            <td className="numeric">{formatQuantity(order.quantity)}</td>
            <td className="numeric">{formatQuantity(order.filledQuantity)}</td>
            <td className="numeric">{formatQuantity(order.quantity - order.filledQuantity)}</td>
            <td>
              <OrderStatusBadge status={order.status} />
              {order.status === 'REJECTED' && (
                <span className="table__sub">{rejectReasonLabel(order.rejectReason)}</span>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
