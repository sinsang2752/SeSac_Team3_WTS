import { useEffect, useMemo, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { Panel } from '../../../components/Panel'
import { PanelState } from '../../../components/PanelState'
import { fetchStocks, fetchStocksBySymbols } from '../../../lib/api'
import { directionOf, formatPrice, formatRate } from '../../../lib/format'
import type { Stock } from '../../../lib/types'
import { useMarketStore } from '../../../stores/marketStore'
import { useUserStore } from '../../../stores/userStore'
import { WatchStar } from '../../watchlist/components/WatchStar'
import { useWatchlist } from '../../watchlist/useWatchlist'

interface Props {
  onSelect: (symbol: string) => void
  selectedSymbol: string | null
  /** 지금 목록에 보이는 종목. 상위가 이 종목만 시세를 구독한다 (CLAUDE.md §57.3). */
  onVisibleSymbolsChange?: (symbols: string[]) => void
}

/** 검색 결과 한 번에 보여줄 개수. 더 찾으려면 검색어를 좁힌다. */
const RESULT_SIZE = 30
/** 타자를 칠 때마다 요청하지 않는다. */
const SEARCH_DELAY_MS = 250

/**
 * 종목 검색과 관심종목. (CLAUDE.md §28, §57.3, ui-requirements §5.2)
 *
 * <p>검색어를 치면 서버에서 전 종목(약 2,700개)을 찾는다. 비우면 관심종목만 보여준다.
 * 전체 목록을 받아 두지 않는다. 별은 관심종목만 바꾼다. 행 선택과 분리되어 있다.
 */
export function StockSearch({ onSelect, selectedSymbol, onVisibleSymbolsChange }: Props) {
  const [keyword, setKeyword] = useState('')
  const query = useDebounced(keyword.trim(), SEARCH_DELAY_MS)
  const searching = query.length > 0
  const prices = useMarketStore((state) => state.prices)
  const session = useUserStore((state) => state.session)
  const { data: watchlist } = useWatchlist()

  const watchedSymbols = useMemo(() => (watchlist ?? []).map((item) => item.symbol), [watchlist])

  const search = useQuery({
    queryKey: ['stocks', 'search', query],
    queryFn: () => fetchStocks({ keyword: query, size: RESULT_SIZE }),
    enabled: searching,
    staleTime: 60 * 1000,
    // 한 글자 더 칠 때 목록이 비었다가 다시 차지 않게 이전 결과를 들고 있는다.
    placeholderData: keepPreviousData,
  })

  const watched = useQuery({
    queryKey: ['stocks', 'bySymbols', [...watchedSymbols].sort()],
    queryFn: () => fetchStocksBySymbols(watchedSymbols),
    enabled: !searching && watchedSymbols.length > 0,
    staleTime: 60 * 60 * 1000,
  })

  const visible: Stock[] = useMemo(() => {
    if (searching) return search.data?.items ?? []
    // 담은 순서를 지킨다. 서버 응답 순서에 기대지 않는다.
    const bySymbol = new Map((watched.data ?? []).map((stock) => [stock.symbol, stock]))
    return watchedSymbols.flatMap((symbol) => bySymbol.get(symbol) ?? [])
  }, [searching, search.data, watched.data, watchedSymbols])

  const visibleKey = visible.map((stock) => stock.symbol).join(',')
  useEffect(() => {
    onVisibleSymbolsChange?.(visibleKey.length > 0 ? visibleKey.split(',') : [])
  }, [visibleKey, onVisibleSymbolsChange])

  const total = searching ? (search.data?.totalElements ?? 0) : visible.length
  const loading = searching ? search.isPending : watchedSymbols.length > 0 && watched.isPending

  return (
    <Panel
      className="workspace__watch"
      title={searching ? '검색 결과' : '관심종목'}
      meta={<span className="panel__count numeric">{total.toLocaleString('ko-KR')}</span>}
    >
      <label className="stocklist__search">
        <span className="stocklist__search-icon" aria-hidden="true">
          ⌕
        </span>
        <input
          type="search"
          placeholder="종목명 또는 종목코드 검색"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          aria-label="종목 검색"
        />
      </label>

      <div className="stocklist__legend">
        <span>종목</span>
        <span>현재가 · 등락률</span>
      </div>

      {search.isError && searching ? (
        <PanelState tone="error" hint="잠시 후 다시 검색해 주세요.">
          종목을 찾지 못했습니다.
        </PanelState>
      ) : loading ? (
        <PanelState tone="loading">종목을 불러오는 중…</PanelState>
      ) : visible.length === 0 ? (
        <PanelState
          hint={
            searching
              ? '종목명이나 코드를 확인해 주세요.'
              : session
                ? '종목을 검색하고 ☆ 를 누르면 관심종목에 담깁니다.'
                : undefined
          }
        >
          {searching ? '검색 결과가 없습니다.' : '종목명이나 종목코드로 검색해 보세요.'}
        </PanelState>
      ) : (
        <ul className="stocklist__rows">
          {visible.map((stock) => {
            const tick = prices[stock.symbol]
            const direction = tick ? directionOf(tick.change) : 'flat'
            const selected = stock.symbol === selectedSymbol

            return (
              <li
                key={stock.symbol}
                className={`stockrow${selected ? ' stockrow--selected' : ''}`}
              >
                <button
                  type="button"
                  className="stockrow__select"
                  onClick={() => onSelect(stock.symbol)}
                  aria-current={selected}
                >
                  <span>
                    <span className="stockrow__name">{stock.name}</span>
                    <span className="stockrow__symbol">{stock.symbol}</span>
                  </span>
                  <span className="stockrow__quote">
                    <span className="stockrow__price">{tick ? formatPrice(tick.price) : '—'}</span>
                    <span className={`stockrow__rate price--${direction}`}>
                      {tick ? formatRate(tick.changeRate) : ''}
                    </span>
                  </span>
                </button>
                {session && <WatchStar symbol={stock.symbol} name={stock.name} />}
              </li>
            )
          })}
        </ul>
      )}

      {searching && total > visible.length && (
        <p className="book__hint">
          {total.toLocaleString('ko-KR')}개 중 {visible.length}개를 보여줍니다. 검색어를 더 입력해 좁혀 보세요.
        </p>
      )}
    </Panel>
  )
}

/** 값이 delay 동안 바뀌지 않으면 그 값을 돌려준다. */
function useDebounced<T>(value: T, delay: number): T {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return settled
}
