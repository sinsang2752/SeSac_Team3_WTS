import { useState } from 'react'

import { Panel } from '../components/Panel'
import { ExecutionHistory } from '../features/trading/components/ExecutionHistory'
import { OpenOrders } from '../features/trading/components/OpenOrders'
import { OrderHistory } from '../features/trading/components/OrderHistory'
import { useFillWatcher, useOpenOrders } from '../features/trading/useTradingQueries'

type Tab = 'open' | 'orders' | 'executions'

/**
 * `/orders` — 거래내역 (CLAUDE.md §26, ui-requirements §8)
 *
 * <p>미체결 주문을 따로 둔 이유는 취소가 여기서만 가능하기 때문이다.
 * 주문내역에는 취소·거절까지 포함한 모든 주문이 남는다.
 *
 * <p>기간·종목·상태 필터는 아직 없다. 조회 API가 상태 외의 조건을 받지 않는다.
 */
export function OrdersPage() {
  const [tab, setTab] = useState<Tab>('open')
  useFillWatcher()
  const { data: openOrders } = useOpenOrders()

  const tabs: { id: Tab; label: string; count?: number }[] = [
    { id: 'open', label: '미체결 주문', count: openOrders?.length },
    { id: 'orders', label: '주문내역' },
    { id: 'executions', label: '체결내역' },
  ]

  return (
    <div className="page">
      <Panel>
        <div className="tabs" role="tablist" aria-label="거래내역">
          {tabs.map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={tab === item.id}
              className="tabs__tab"
              onClick={() => setTab(item.id)}
            >
              {item.label}
              {item.count !== undefined && item.count > 0 && (
                <span className="tabs__count numeric">{item.count}</span>
              )}
            </button>
          ))}
        </div>
        <div className="table-scroll">
          {tab === 'open' && <OpenOrders />}
          {tab === 'orders' && <OrderHistory />}
          {tab === 'executions' && <ExecutionHistory />}
        </div>
      </Panel>
    </div>
  )
}
