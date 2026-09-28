import { Panel } from '../../../components/Panel'
import { PanelState } from '../../../components/PanelState'
import { formatPrice, formatVolume } from '../../../lib/format'
import type { OrderBookLevel } from '../../../lib/types'
import { useMarketStore } from '../../../stores/marketStore'

interface Props {
  symbol: string
  /** 호가를 누르면 지정가 입력을 채운다. 주문을 제출하지는 않는다. */
  onSelectPrice: (price: number) => void
}

/** 시안 기준 단계 수. 서버가 더 내려줘도 화면은 5단계만 보여준다 (ui-requirements §5.3). */
const LEVELS = 5

/**
 * 호가. (CLAUDE.md §28, ui-requirements §5.3)
 *
 * <p>높은 가격이 위, 낮은 가격이 아래다. 최우선 매도·매수호가가 가운데에서 맞닿는다.
 * 매도는 파랑, 매수는 빨강이다. 이 색은 매매 방향을 뜻하며 전일 대비 등락과는 별개다.
 */
export function OrderBookPanel({ symbol, onSelectPrice }: Props) {
  const book = useMarketStore((state) => state.orderBooks[symbol])
  const tick = useMarketStore((state) => state.prices[symbol])

  if (!book) {
    return (
      <Panel title="호가" meta={<span className="panel__meta">가격 · 잔량</span>}>
        <PanelState tone="loading">호가를 기다리는 중…</PanelState>
      </Panel>
    )
  }

  const asks = book.asks.slice(0, LEVELS)
  const bids = book.bids.slice(0, LEVELS)
  const maxQuantity = Math.max(
    ...asks.map((level) => level.quantity),
    ...bids.map((level) => level.quantity),
    1,
  )

  const row = (level: OrderBookLevel, side: 'ask' | 'bid') => (
    <button
      key={`${side}-${level.price}`}
      type="button"
      className={`book__row price--${side === 'ask' ? 'down' : 'up'}`}
      onClick={() => onSelectPrice(level.price)}
      aria-label={`${formatPrice(level.price)}원을 주문가격으로 입력`}
    >
      <span
        className="book__depth"
        style={{ width: `${(level.quantity / maxQuantity) * 100}%` }}
        aria-hidden="true"
      />
      <span className="book__price">{formatPrice(level.price)}</span>
      <span className="book__quantity">{formatVolume(level.quantity)}</span>
    </button>
  )

  return (
    <Panel title="호가" meta={<span className="panel__meta">가격 · 잔량</span>}>
      <div className="book__section book__section--ask">
        <span>매도호가</span>
        <span>잔량</span>
      </div>
      {/* 매도는 높은 가격이 위다. 서버는 최우선(가장 낮은)부터 주므로 뒤집는다. */}
      <div className="book__rows">{[...asks].reverse().map((level) => row(level, 'ask'))}</div>

      <div className="book__current">
        <strong>{tick ? `${formatPrice(tick.price)}원` : '—'}</strong>
        <span>현재가</span>
      </div>

      <div className="book__rows">{bids.map((level) => row(level, 'bid'))}</div>
      <div className="book__section book__section--bid">
        <span>매수호가</span>
        <span>잔량</span>
      </div>

      <p className="book__hint">호가를 누르면 지정가 주문가격에 입력됩니다.</p>
    </Panel>
  )
}
