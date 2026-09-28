import { useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'

import { Modal } from '../../../components/Modal'
import { Pair } from '../../../components/Pair'
import { Panel } from '../../../components/Panel'
import { ApiRequestError } from '../../../lib/api'
import { formatQuantity, formatWon } from '../../../lib/format'
import type { Order, OrderSide, OrderType, Stock } from '../../../lib/types'
import { useMarketStore } from '../../../stores/marketStore'
import { useToastStore } from '../../../stores/toastStore'
import { useAccount, usePlaceOrder, usePositions, useRefreshTrading } from '../useTradingQueries'
import { rejectReasonLabel } from '../orderStatus'

interface Props {
  stock: Stock
  /** 호가를 눌러 들어온 지정가 제안. nonce가 바뀌면 다시 반영한다. */
  priceSuggestion: { price: number; nonce: number } | null
}

/**
 * 주문 결과. 서버가 답하기 전에는 절대 만들지 않는다.
 *
 * <p>`unknown`은 요청이 서버에 닿았는지조차 모르는 상태다. 실패로 단정하지 않는다.
 * (ui-requirements §5.5)
 */
type Feedback =
  | { kind: 'filled'; order: Order }
  | { kind: 'accepted'; order: Order }
  | { kind: 'rejected'; message: string; code: string }
  | { kind: 'unknown' }

const QUICK_QUANTITIES = [1, 5, 10]

/**
 * 주문 입력. (CLAUDE.md §27, §28, ui-requirements §5.4 – §5.5)
 *
 * <p>입력 순서는 매수·매도 → 시장가·지정가 → 가격 → 수량 → 금액 확인 → 주문이다.
 * 주문 종류는 시장가와 지정가 둘뿐이다 (§9.7).
 *
 * <p>화면의 검증은 실수를 미리 막기 위한 것이고, 체결 여부와 거절은 서버가 정한다.
 * 예수금·예약금·보유수량은 전부 서버 응답을 그대로 보여준다. 여기서 다시 계산하지 않는다.
 */
export function OrderForm({ stock, priceSuggestion }: Props) {
  const [side, setSide] = useState<OrderSide>('BUY')
  const [orderType, setOrderType] = useState<OrderType>('LIMIT')
  const [quantity, setQuantity] = useState('')
  /** null이면 아직 사용자가 지정가를 건드리지 않았다는 뜻이다. */
  const [limitPriceInput, setLimitPriceInput] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [renderedSymbol, setRenderedSymbol] = useState(stock.symbol)
  const [appliedNonce, setAppliedNonce] = useState(0)

  const tick = useMarketStore((state) => state.prices[stock.symbol])
  const { data: account } = useAccount()
  const { data: positions } = usePositions()
  const mutation = usePlaceOrder()
  const refresh = useRefreshTrading()
  const pushToast = useToastStore((state) => state.push)

  const position = positions?.find((item) => item.symbol === stock.symbol)
  const currentPrice = tick?.price ?? null

  /**
   * 같은 주문 시도에는 같은 키를 쓴다 (CLAUDE.md §14).
   *
   * <p>서버가 답을 준 뒤에만 키를 새로 만든다. 응답을 받지 못했을 때 같은 키로 다시 보내면
   * 주문이 두 건 생기지 않는다.
   */
  const idempotencyKey = useRef(crypto.randomUUID())

  // 종목이 바뀌면 입력을 비운다. 매수/매도 탭과 주문 종류는 그대로 둔다.
  // 렌더 중 상태 조정이라 effect보다 한 번 덜 그린다.
  if (renderedSymbol !== stock.symbol) {
    setRenderedSymbol(stock.symbol)
    setQuantity('')
    setLimitPriceInput(null)
    setFeedback(null)
  }

  // 호가를 누르면 지정가로 바꾸고 가격을 채운다. 주문을 제출하지는 않는다.
  if (priceSuggestion && priceSuggestion.nonce !== appliedNonce) {
    setAppliedNonce(priceSuggestion.nonce)
    setOrderType('LIMIT')
    setLimitPriceInput(String(priceSuggestion.price))
  }

  // 손대지 않은 지정가는 현재가를 따라간다. 한 번 입력하면 그 값이 유지된다.
  const limitPrice = limitPriceInput ?? (currentPrice === null ? '' : String(currentPrice))

  const parsedQuantity = Number(quantity)
  const parsedLimitPrice = Number(limitPrice)
  const hasQuantity = Number.isSafeInteger(parsedQuantity) && parsedQuantity >= 1
  const hasLimitPrice = Number.isSafeInteger(parsedLimitPrice) && parsedLimitPrice >= 1

  // 시장가는 현재가, 지정가는 입력한 가격으로 금액을 낸다.
  const referencePrice = orderType === 'MARKET' ? currentPrice : hasLimitPrice ? parsedLimitPrice : null
  const estimate = hasQuantity && referencePrice !== null ? referencePrice * parsedQuantity : null

  const availableQuantity = position?.availableQuantity ?? 0
  const maxQuantity =
    side === 'SELL'
      ? availableQuantity
      : account && referencePrice ? Math.floor(account.availableCash / referencePrice) : 0

  const error = validate()
  const submittable = error === null && !mutation.isPending

  /** 무엇이 잘못됐는지와 어느 입력란 때문인지를 함께 돌려준다. 색만으로 알리지 않는다. */
  function validate(): { field: 'quantity' | 'price' | null; message: string } | null {
    if (quantity.trim() === '' || !hasQuantity) {
      return { field: 'quantity', message: '수량은 1주 이상의 정수로 입력해 주세요.' }
    }
    if (orderType === 'LIMIT' && !hasLimitPrice) {
      return { field: 'price', message: '주문가격을 올바르게 입력해 주세요.' }
    }
    if (orderType === 'MARKET' && currentPrice === null) {
      return { field: null, message: '최신 시세를 확인한 뒤 주문할 수 있어요.' }
    }
    if (side === 'BUY' && account && estimate !== null && estimate > account.availableCash) {
      return { field: 'quantity', message: '주문가능금액이 부족합니다.' }
    }
    if (side === 'SELL' && parsedQuantity > availableQuantity) {
      return { field: 'quantity', message: '매도가능수량을 초과했습니다.' }
    }
    return null
  }

  // 아무것도 입력하지 않은 처음부터 빨간 글씨를 띄우지 않는다.
  const shownError = quantity.trim() === '' ? null : error

  function openConfirm(event: FormEvent) {
    event.preventDefault()
    if (!submittable) return
    setConfirming(true)
  }

  function submit() {
    if (mutation.isPending) return

    mutation.mutate(
      {
        idempotencyKey: idempotencyKey.current,
        order: {
          symbol: stock.symbol,
          side,
          orderType,
          quantity: parsedQuantity,
          limitPrice: orderType === 'LIMIT' ? parsedLimitPrice : undefined,
        },
      },
      {
        onSuccess: (order) => {
          idempotencyKey.current = crypto.randomUUID()
          setConfirming(false)
          setQuantity('')
          // 접수와 체결은 다른 사건이다. 서버가 준 상태 그대로 구분해서 알린다.
          if (order.status === 'FILLED') {
            // 체결 알림은 체결 기록을 지켜보는 쪽(useFillWatcher)이 한 곳에서 낸다.
            // 여기서도 알리면 시장가 주문만 토스트가 두 번 뜬다.
            setFeedback({ kind: 'filled', order })
          } else {
            setFeedback({ kind: 'accepted', order })
            pushToast('주문이 접수되었습니다. 지정한 가격 조건을 기다리고 있어요.')
          }
        },
        onError: (cause) => {
          setConfirming(false)
          // 서버가 공통 에러 형식(§39)으로 답했다면 주문은 확실히 거절됐다. 그때만 키를 새로 만든다.
          if (cause instanceof ApiRequestError && cause.isDomainError) {
            idempotencyKey.current = crypto.randomUUID()
            setFeedback({ kind: 'rejected', message: cause.message, code: cause.code })
            pushToast(cause.message, 'error')
            return
          }
          // 여기서는 주문이 성립했는지 알 수 없다. 실패로 단정하지도, 자동으로 다시 보내지도 않는다.
          setFeedback({ kind: 'unknown' })
          pushToast('주문 결과를 확인하고 있어요.', 'error')
          refresh()
        },
      },
    )
  }

  const sideLabel = side === 'BUY' ? '매수' : '매도'
  const typeLabel = orderType === 'MARKET' ? '시장가' : '지정가'

  return (
    <Panel
      title="주문하기"
      meta={<span className="panel__meta">모의투자</span>}
      className="order"
    >
      <div className="sidetabs" role="tablist" aria-label="매매 구분">
        {(['BUY', 'SELL'] as const).map((value) => (
          <button
            key={value}
            type="button"
            role="tab"
            data-side={value}
            aria-selected={side === value}
            className="sidetabs__tab"
            onClick={() => setSide(value)}
          >
            {value === 'BUY' ? '매수' : '매도'}
          </button>
        ))}
      </div>

      <form className="order__form" onSubmit={openConfirm}>
        <fieldset className="segmented order__types" role="tablist" aria-label="주문 종류">
          {(['LIMIT', 'MARKET'] as const).map((value) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={orderType === value}
              className="segmented__option"
              onClick={() => setOrderType(value)}
            >
              {value === 'MARKET' ? '시장가' : '지정가'}
            </button>
          ))}
        </fieldset>

        <label className="field">
          <span className="field__label">
            주문가격
            <span className="field__hint">
              {orderType === 'MARKET' ? '현재 시장가격으로 체결' : '가격 조건을 만족하면 체결'}
            </span>
          </span>
          <span
            className={`field__control${shownError?.field === 'price' ? ' field__control--error' : ''}`}
          >
            <input
              type="number"
              inputMode="numeric"
              min={1}
              step={1}
              value={orderType === 'MARKET' ? '' : limitPrice}
              placeholder={orderType === 'MARKET' && currentPrice !== null ? String(currentPrice) : ''}
              disabled={orderType === 'MARKET'}
              onChange={(event) => setLimitPriceInput(event.target.value)}
              aria-label="주문가격"
            />
            <span className="field__unit">원</span>
          </span>
        </label>

        <label className="field">
          <span className="field__label">
            주문수량
            <span className="field__hint">주</span>
          </span>
          <span
            className={`field__control${shownError?.field === 'quantity' ? ' field__control--error' : ''}`}
          >
            <button
              type="button"
              className="field__step"
              aria-label="수량 1 줄이기"
              onClick={() => setQuantity(String(Math.max(1, (Number(quantity) || 1) - 1)))}
            >
              −
            </button>
            <input
              type="number"
              inputMode="numeric"
              min={1}
              step={1}
              placeholder="0"
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
              aria-label="주문수량"
            />
            <span className="field__unit">주</span>
            <button
              type="button"
              className="field__step"
              aria-label="수량 1 늘리기"
              onClick={() => setQuantity(String((Number(quantity) || 0) + 1))}
            >
              +
            </button>
          </span>
        </label>

        <div className="order__quick">
          {QUICK_QUANTITIES.map((value) => (
            <button key={value} type="button" className="btn" onClick={() => setQuantity(String(value))}>
              {value}주
            </button>
          ))}
          <button
            type="button"
            className="btn"
            disabled={maxQuantity < 1}
            onClick={() => setQuantity(String(maxQuantity))}
          >
            최대
          </button>
        </div>

        <div className="order__availability">
          {side === 'BUY' ? (
            <>
              <Pair label="예수금" value={account ? formatWon(account.cashBalance) : '—'} />
              <Pair label="주문 예약금" value={account ? formatWon(account.reservedCash) : '—'} />
              <Pair
                label="주문가능금액"
                value={account ? formatWon(account.availableCash) : '—'}
                emphasis
              />
            </>
          ) : (
            <>
              <Pair label="보유수량" value={formatQuantity(position?.quantity ?? 0)} />
              <Pair label="매도 예약수량" value={formatQuantity(position?.reservedQuantity ?? 0)} />
              <Pair label="매도가능수량" value={formatQuantity(availableQuantity)} emphasis />
            </>
          )}
        </div>

        <div className="order__total">
          <span>{orderType === 'MARKET' ? '예상 주문금액' : '주문금액'}</span>
          <strong>{estimate === null ? '—' : formatWon(estimate)}</strong>
        </div>

        {shownError && (
          <p className="field__error" role="alert">
            {shownError.message}
          </p>
        )}

        <button
          type="submit"
          className={`btn btn--block btn--cta btn--${side === 'BUY' ? 'buy' : 'sell'}`}
          disabled={!submittable}
        >
          {mutation.isPending ? '주문 요청 중…' : `모의 ${sideLabel}`}
        </button>

        <p className="order__note">
          가상 자금으로 거래해요.
          <br />
          실제 주식 주문은 발생하지 않습니다.
        </p>
      </form>

      {feedback && <OrderFeedback feedback={feedback} stock={stock} />}

      <Modal
        open={confirming}
        busy={mutation.isPending}
        title={`모의 ${sideLabel} 주문을 확인해 주세요`}
        onClose={() => setConfirming(false)}
        note={
          <>
            가상 자금으로 진행되는 모의투자입니다.
            {orderType === 'MARKET' && ' 체결 시점에 따라 예상금액과 달라질 수 있어요.'}
          </>
        }
        actions={
          <>
            <button
              type="button"
              className="btn"
              disabled={mutation.isPending}
              onClick={() => setConfirming(false)}
            >
              돌아가기
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={mutation.isPending}
              onClick={submit}
            >
              {mutation.isPending ? '주문 요청 중…' : `모의 ${sideLabel} 주문하기`}
            </button>
          </>
        }
      >
        <Pair label="종목" value={`${stock.name} ${stock.symbol}`} />
        <Pair
          label="주문 구분"
          value={sideLabel}
          valueClassName={side === 'BUY' ? 'price--up' : 'price--down'}
        />
        <Pair label="주문유형" value={typeLabel} />
        <Pair
          label="주문가격"
          value={orderType === 'MARKET' ? '현재 시장가격' : formatWon(parsedLimitPrice)}
        />
        <Pair label="주문수량" value={formatQuantity(parsedQuantity)} />
        <Pair
          label={orderType === 'MARKET' ? '예상 주문금액' : '주문금액'}
          value={estimate === null ? '—' : formatWon(estimate)}
          emphasis
        />
      </Modal>
    </Panel>
  )
}

/** 주문 뒤 상태. 접수·체결·거절·결과 확인 중을 서로 다른 문구로 구분한다. */
function OrderFeedback({ feedback, stock }: { feedback: Feedback; stock: Stock }) {
  if (feedback.kind === 'filled') {
    return (
      <p className="order__feedback order__feedback--filled" role="status">
        {stock.name} {formatQuantity(feedback.order.filledQuantity)}가 모의 체결되었습니다.
      </p>
    )
  }

  if (feedback.kind === 'accepted') {
    return (
      <p className="order__feedback order__feedback--pending" role="status">
        주문이 접수되었습니다. 지정한 가격 조건을 기다리고 있어요.
      </p>
    )
  }

  if (feedback.kind === 'rejected') {
    return (
      <p className="order__feedback order__feedback--error" role="alert">
        주문이 거절되었습니다 · {rejectReasonLabel(feedback.code)}
        <br />
        {feedback.message}
      </p>
    )
  }

  return (
    <p className="order__feedback order__feedback--unknown" role="alert">
      주문 결과를 확인하고 있어요. <Link to="/orders">거래내역</Link>에서 상태를 확인해 주세요.
    </p>
  )
}
