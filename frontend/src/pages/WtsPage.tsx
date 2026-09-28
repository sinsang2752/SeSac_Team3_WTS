import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'

import { Panel } from '../components/Panel'
import { PanelState } from '../components/PanelState'
import { StockChart } from '../features/chart/components/StockChart'
import { OrderBookPanel } from '../features/market/components/OrderBookPanel'
import { RealtimePrice } from '../features/market/components/RealtimePrice'
import { StockSearch } from '../features/market/components/StockSearch'
import { useMarketStream } from '../features/market/useMarketStream'
import { ExecutionHistory } from '../features/trading/components/ExecutionHistory'
import { OpenOrders } from '../features/trading/components/OpenOrders'
import { OrderForm } from '../features/trading/components/OrderForm'
import { PositionTable } from '../features/trading/components/PositionTable'
import { useFillWatcher, useOpenOrders } from '../features/trading/useTradingQueries'
import { fetchStocks } from '../lib/api'
import { useMarketStore } from '../stores/marketStore'
import { useToastStore } from '../stores/toastStore'
import { useUserStore } from '../stores/userStore'

type BottomTab = 'openOrders' | 'executions' | 'positions'

/**
 * `/wts` 와 `/wts/:symbol` (CLAUDE.md §26, §27, ui-requirements §5.1)
 *
 * <p>선택한 종목이 URL에 있다. 새로고침해도 같은 종목이 열리고 링크를 공유할 수 있다.
 * 왼쪽은 검색·관심종목, 가운데는 현재가·차트·내역, 오른쪽은 주문·호가다.
 */
export function WtsPage() {
  const { symbol: routeSymbol } = useParams()
  const navigate = useNavigate()
  const session = useUserStore((state) => state.session)
  const pushToast = useToastStore((state) => state.push)
  const [bottomTab, setBottomTab] = useState<BottomTab>('openOrders')
  /** 호가를 눌러 넘긴 지정가. 같은 가격을 다시 눌러도 반영되도록 일련번호를 붙인다. */
  const [priceSuggestion, setPriceSuggestion] = useState<{ price: number; nonce: number } | null>(
    null,
  )

  const { data: stocks, isPending, isError } = useQuery({
    queryKey: ['stocks'],
    queryFn: () => fetchStocks(),
    staleTime: 5 * 60 * 1000, // 종목 목록은 자주 바뀌지 않는다.
  })

  const symbols = useMemo(() => (stocks ?? []).map((stock) => stock.symbol), [stocks])
  useMarketStream(symbols)
  // 서버가 미체결 지정가를 자동 체결하면(Phase 4) 계좌·포지션도 따라 바뀐다.
  useFillWatcher()
  const { data: openOrders } = useOpenOrders()

  const selectSymbol = useMarketStore((state) => state.selectSymbol)

  // URL이 선택 상태의 원본이다. 스토어는 시세 컴포넌트들이 참조하도록 맞춰만 둔다.
  const selectedSymbol = routeSymbol ?? symbols[0] ?? null
  useEffect(() => {
    if (selectedSymbol) selectSymbol(selectedSymbol)
  }, [selectedSymbol, selectSymbol])

  const selectedStock = (stocks ?? []).find((stock) => stock.symbol === selectedSymbol)

  const tabs: { id: BottomTab; label: string; count?: number }[] = [
    { id: 'openOrders', label: '미체결 주문', count: openOrders?.length },
    { id: 'executions', label: '체결내역' },
    { id: 'positions', label: '보유종목' },
  ]

  return (
    <div className="workspace">
      <StockSearch
        stocks={stocks ?? []}
        selectedSymbol={selectedSymbol}
        isPending={isPending}
        onSelect={(symbol) => navigate(`/wts/${symbol}`)}
      />

      <div className="workspace__center">
        <Panel>
          {isError ? (
            <PanelState tone="error" hint="잠시 후 다시 확인해 주세요.">
              종목 정보를 불러오지 못했습니다.
            </PanelState>
          ) : selectedStock ? (
            <>
              <RealtimePrice stock={selectedStock} />
              <StockChart symbol={selectedStock.symbol} />
            </>
          ) : (
            <PanelState tone={isPending ? 'loading' : 'empty'}>
              {isPending ? '종목 정보를 불러오는 중…' : '표시할 종목이 없습니다.'}
            </PanelState>
          )}
        </Panel>

        <Panel>
          <div className="tabs" role="tablist" aria-label="거래 현황">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={bottomTab === tab.id}
                className="tabs__tab"
                onClick={() => setBottomTab(tab.id)}
              >
                {tab.label}
                {tab.count !== undefined && tab.count > 0 && (
                  <span className="tabs__count numeric">{tab.count}</span>
                )}
              </button>
            ))}
          </div>

          {session ? (
            <div className="table-scroll">
              {bottomTab === 'openOrders' && <OpenOrders />}
              {bottomTab === 'executions' && <ExecutionHistory />}
              {bottomTab === 'positions' && <PositionTable />}
            </div>
          ) : (
            <PanelState
              hint="로그인하면 주문과 보유종목을 볼 수 있어요."
              action={
                <Link className="btn btn--primary" to="/login">
                  모의투자 시작하기
                </Link>
              }
            >
              아직 거래 기록이 없습니다.
            </PanelState>
          )}
        </Panel>
      </div>

      <div className="workspace__right">
        {selectedStock && session && (
          <OrderForm stock={selectedStock} priceSuggestion={priceSuggestion} />
        )}
        {selectedStock && !session && <LockedOrderPanel />}

        {selectedStock && (
          <OrderBookPanel
            symbol={selectedStock.symbol}
            onSelectPrice={(price) => {
              setPriceSuggestion((current) => ({ price, nonce: (current?.nonce ?? 0) + 1 }))
              pushToast('선택한 호가를 주문가격에 입력했어요.')
            }}
          />
        )}
      </div>
    </div>
  )
}

/** 시세는 로그인 없이 볼 수 있다 (ADR-0007). 주문만 가상 계좌가 필요하다. */
function LockedOrderPanel() {
  return (
    <Panel title="주문하기" meta={<span className="panel__meta">모의투자</span>}>
      <div className="order__locked">
        <p style={{ margin: 0 }}>
          시세와 차트는 로그인 없이 볼 수 있습니다.
          <br />
          주문하려면 가상 계좌가 필요합니다.
        </p>
        <Link className="btn btn--primary" to="/login">
          모의투자 시작하기
        </Link>
      </div>
    </Panel>
  )
}
