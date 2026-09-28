/**
 * 표시 형식. (docs/learnstock/ui-requirements.md §4)
 *
 * 가격 `74,500원`, 수량 `10주`, 수익률 `+3.47%`, 시각 `10:32:15`.
 * 내역에는 날짜도 함께 보인다. 시각은 모두 Asia/Seoul로 변환한다 (CLAUDE.md §43).
 */

const KRW = new Intl.NumberFormat('ko-KR')
const SEOUL_TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})
const SEOUL_HOUR_MINUTE = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})
const SEOUL_DATE_TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

/** 자릿수만. 단위가 이미 화면에 있는 자리(표 머리글 등)에서 쓴다. */
export function formatPrice(value: number): string {
  return KRW.format(Math.round(value))
}

/** 단위까지. 단독으로 읽히는 금액에 쓴다. */
export function formatWon(value: number): string {
  return `${formatPrice(value)}원`
}

export function formatQuantity(value: number): string {
  return `${KRW.format(value)}주`
}

/** 부호를 반드시 붙인다. 색만으로 방향을 전달하지 않기 위해서다. */
export function formatChange(value: number): string {
  const sign = value > 0 ? '+' : ''
  return `${sign}${KRW.format(Math.round(value))}`
}

export function formatChangeWon(value: number): string {
  return `${formatChange(value)}원`
}

export function formatRate(value: number): string {
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(2)}%`
}

/** `▲ +1,200원 (+1.64%)` — 아이콘·부호·색을 함께 쓴다. */
export function formatPriceChange(change: number, rate: number): string {
  const arrow = change > 0 ? '▲' : change < 0 ? '▼' : '―'
  return `${arrow} ${formatChangeWon(change)} (${formatRate(rate)})`
}

export function formatVolume(value: number): string {
  return KRW.format(value)
}

/** UTC ISO-8601 → 한국 시각 HH:mm:ss */
export function formatSeoulTime(isoUtc: string): string {
  return SEOUL_TIME.format(new Date(isoUtc))
}

/** UNIX 초 → 한국 시각 HH:mm. 차트 시간축에서 쓴다. */
export function formatSeoulHourMinute(epochSeconds: number): string {
  return SEOUL_HOUR_MINUTE.format(new Date(epochSeconds * 1000))
}

/** UNIX 초 → 한국 시각 HH:mm:ss. 차트 십자선에서 쓴다. */
export function formatSeoulTimeFromEpoch(epochSeconds: number): string {
  return SEOUL_TIME.format(new Date(epochSeconds * 1000))
}

/** UTC ISO-8601 → 한국 시각 MM. DD. HH:mm:ss. 내역 표에서 쓴다. */
export function formatSeoulDateTime(isoUtc: string): string {
  return SEOUL_DATE_TIME.format(new Date(isoUtc))
}

/** 등락 방향. 상승은 적색, 하락은 청색이다 (tokens.css). */
export type PriceDirection = 'up' | 'down' | 'flat'

export function directionOf(change: number): PriceDirection {
  if (change > 0) return 'up'
  if (change < 0) return 'down'
  return 'flat'
}
