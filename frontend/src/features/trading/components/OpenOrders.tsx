import { useState } from 'react'

import { Modal } from '../../../components/Modal'
import { Pair } from '../../../components/Pair'
import { PanelState } from '../../../components/PanelState'
import { ApiRequestError } from '../../../lib/api'
import { formatPrice, formatQuantity, formatSeoulDateTime, formatWon } from '../../../lib/format'
import type { Order } from '../../../lib/types'
import { useStockName } from '../../market/useStockName'
import { useToastStore } from '../../../stores/toastStore'
import { useCancelOrder, useOpenOrders, useRefreshTrading } from '../useTradingQueries'
import { OrderStatusBadge } from './OrderStatusBadge'

/**
 * 미체결 주문. (CLAUDE.md §28 – OpenOrders, ui-requirements §6)
 *
 * <p>가격 조건을 만족하지 않아 대기 중인 지정가 주문이다. 조건에 도달하면 서버가 자동으로
 * 체결한다 (Phase 4).
 *
 * <p>취소는 확인을 거친다. 응답을 받기 전에는 예약금이나 예약수량이 풀린 것처럼 보이지 않는다.
 * 취소와 체결이 동시에 일어나면 서버가 거절하고, 화면은 최신 상태를 다시 읽는다.
 */
export function OpenOrders() {
  const { data: orders, isPending, isError } = useOpenOrders()
  const cancel = useCancelOrder()
  const refresh = useRefreshTrading()
  const pushToast = useToastStore((state) => state.push)
  const nameOf = useStockName()
  const [target, setTarget] = useState<Order | null>(null)

  if (isPending) return <PanelState tone="loading">불러오는 중…</PanelState>
  if (isError) {
    return (
      <PanelState tone="error" hint="잠시 후 다시 확인해 주세요.">
        미체결 주문을 불러오지 못했습니다.
      </PanelState>
    )
  }
  if (!orders || orders.length === 0) {
    return <PanelState hint="지정가 주문을 내면 여기에서 기다립니다.">미체결 주문이 없습니다.</PanelState>
  }

  function requestCancel() {
    if (!target || cancel.isPending) return
    const order = target
    cancel.mutate(order.orderId, {
      onSuccess: () => {
        setTarget(null)
        pushToast('주문이 취소되었습니다. 예약이 해제되었어요.')
      },
      onError: (cause) => {
        setTarget(null)
        const alreadyDone =
          cause instanceof ApiRequestError && cause.code === 'INVALID_ORDER_STATE'
        pushToast(
          alreadyDone ? '이미 체결되어 취소할 수 없습니다.' : '주문을 취소하지 못했습니다.',
          'error',
        )
        // 취소와 체결이 동시에 일어난 경우다. 최신 주문·계좌 상태를 다시 읽는다.
        refresh()
      },
    })
  }

  return (
    <>
      <table className="table">
        <thead>
          <tr>
            <th scope="col">종목 · 주문시각</th>
            <th scope="col">구분</th>
            <th scope="col">주문유형</th>
            <th scope="col">주문가격</th>
            <th scope="col">주문수량</th>
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
              <td className="numeric">{formatQuantity(order.quantity - order.filledQuantity)}</td>
              <td>
                <OrderStatusBadge status={order.status} />
                <button
                  type="button"
                  className="btn btn--sm"
                  style={{ marginLeft: 'var(--space-2)' }}
                  disabled={cancel.isPending}
                  onClick={() => setTarget(order)}
                >
                  취소
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <Modal
        open={target !== null}
        busy={cancel.isPending}
        title="미체결 주문을 취소할까요?"
        onClose={() => setTarget(null)}
        note="취소가 끝나면 예약된 금액 또는 수량을 다시 사용할 수 있어요."
        actions={
          <>
            <button
              type="button"
              className="btn"
              disabled={cancel.isPending}
              onClick={() => setTarget(null)}
            >
              유지하기
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={cancel.isPending}
              onClick={requestCancel}
            >
              {cancel.isPending ? '취소 요청 중…' : '취소 요청'}
            </button>
          </>
        }
      >
        {target && (
          <>
            <Pair label="종목" value={`${nameOf(target.symbol)} ${target.symbol}`} />
            <Pair
              label="주문 구분"
              value={target.side === 'BUY' ? '매수' : '매도'}
              valueClassName={target.side === 'BUY' ? 'price--up' : 'price--down'}
            />
            <Pair
              label="주문가격"
              value={target.limitPrice === undefined ? '시장가' : formatWon(target.limitPrice)}
            />
            <Pair
              label="미체결수량"
              value={formatQuantity(target.quantity - target.filledQuantity)}
              emphasis
            />
          </>
        )}
      </Modal>
    </>
  )
}
