import { useMemo, useState } from 'react'

import { Panel } from '../../../components/Panel'
import { PanelState } from '../../../components/PanelState'
import { directionOf, formatPrice, formatRate } from '../../../lib/format'
import type { Stock } from '../../../lib/types'
import { useMarketStore } from '../../../stores/marketStore'
import { useUserStore } from '../../../stores/userStore'
import { WatchStar } from '../../watchlist/components/WatchStar'
import { useWatchlist } from '../../watchlist/useWatchlist'

interface Props {
  stocks: Stock[]
  onSelect: (symbol: string) => void
  selectedSymbol: string | null
  /** 종목 목록을 아직 받지 못했다. */
  isPending?: boolean
}

/**
 * 종목 검색과 관심종목. (CLAUDE.md §28, ui-requirements §5.2)
 *
 * <p>검색어를 치면 전체 종목에서 찾고, 비우면 관심종목만 보여준다.
 * 별은 관심종목만 바꾼다. 행 선택과 분리되어 있다.
 *
 * <p>서버 검색(`?keyword=`)을 쓰지 않는 이유는 종목 목록이 이미 클라이언트에 있기 때문이다.
 * 종목 수가 늘어나면 서버 검색으로 바꾼다.
 */
export function StockSearch({ stocks, onSelect, selectedSymbol, isPending }: Props) {
  const [keyword, setKeyword] = useState('')
  const prices = useMarketStore((state) => state.prices)
  const session = useUserStore((state) => state.session)
  const { data: watchlist } = useWatchlist()

  const watchedSymbols = useMemo(
    () => new Set((watchlist ?? []).map((item) => item.symbol)),
    [watchlist],
  )

  const trimmed = keyword.trim()
  const searching = trimmed.length > 0
  // 관심종목이 비어 있으면 전체를 보여준다. 빈 화면에서 시작하지 않게 한다.
  const showingAll = !searching && watchedSymbols.size === 0

  const visible = useMemo(() => {
    if (searching) {
      const needle = trimmed.toLowerCase()
      return stocks.filter(
        (stock) => stock.symbol.includes(trimmed) || stock.name.toLowerCase().includes(needle),
      )
    }
    if (watchedSymbols.size === 0) return stocks
    return stocks.filter((stock) => watchedSymbols.has(stock.symbol))
  }, [searching, trimmed, stocks, watchedSymbols])

  const title = searching ? '검색 결과' : showingAll ? '전체 종목' : '관심종목'

  return (
    <Panel
      className="workspace__watch"
      title={title}
      meta={<span className="panel__count numeric">{visible.length}</span>}
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

      {isPending ? (
        <PanelState tone="loading">종목을 불러오는 중…</PanelState>
      ) : visible.length === 0 ? (
        <PanelState hint={searching ? '종목명이나 코드를 확인해 주세요.' : undefined}>
          {searching ? '검색 결과가 없습니다.' : '표시할 종목이 없습니다.'}
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

      {showingAll && session && (
        <p className="book__hint">
          관심종목을 추가해 보세요. 종목 옆의 ☆ 를 누르면 등록됩니다.
        </p>
      )}
    </Panel>
  )
}
