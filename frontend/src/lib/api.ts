import type {
  Account,
  ApiError,
  Candle,
  Execution,
  MockLoginResult,
  Order,
  OrderStatus,
  PlaceOrderRequest,
  Portfolio,
  Position,
  Stock,
  WatchlistItem,
} from './types'

/**
 * 브라우저는 항상 같은 오리진으로 호출하고 Vite 개발 서버가 Gateway로 넘긴다.
 * 배포 환경에서는 Gateway가 프론트엔드와 같은 오리진에 선다 (CLAUDE.md §6.1).
 */
export class ApiRequestError extends Error {
  readonly code: string
  readonly traceId: string | null

  constructor(error: ApiError) {
    super(error.message)
    this.name = 'ApiRequestError'
    this.code = error.code
    this.traceId = error.traceId
  }

  /**
   * 서버가 공통 에러 형식(§39)으로 답했는가.
   *
   * <p>false면 요청이 서버에 닿았는지조차 알 수 없다. 주문 재시도 판단에 쓴다.
   */
  get isDomainError(): boolean {
    return !this.code.startsWith('HTTP_')
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'DELETE'
  /** Gateway가 검증하는 Bearer 토큰. 시세 조회는 필요 없다 (ADR-0007). */
  token?: string | null
  body?: unknown
  /** 주문 중복 생성 방지 키 (CLAUDE.md §14). */
  idempotencyKey?: string
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.token) headers.Authorization = `Bearer ${options.token}`
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    // 서버가 공통 에러 형식을 돌려주지 못하는 경우(프록시 오류 등)도 있다.
    const fallback: ApiError = {
      code: 'HTTP_' + response.status,
      message: `요청이 실패했습니다 (${response.status})`,
      traceId: response.headers.get('X-Trace-Id'),
      timestamp: new Date().toISOString(),
    }
    let body: ApiError = fallback
    try {
      body = { ...fallback, ...(await response.json()) }
    } catch {
      // 본문이 JSON이 아니면 fallback을 그대로 쓴다.
    }
    throw new ApiRequestError(body)
  }

  // DELETE는 204 No Content다. 본문이 없으므로 파싱하지 않는다.
  if (response.status === 204) return undefined as T

  return (await response.json()) as T
}

// ── 시세 (인증 불필요) ────────────────────────────────────────

export function fetchStocks(keyword?: string): Promise<Stock[]> {
  const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''
  return request<Stock[]>(`/api/market/stocks${query}`)
}

export function fetchCandles(symbol: string, limit = 120): Promise<Candle[]> {
  return request<Candle[]>(
    `/api/market/stocks/${symbol}/candles?interval=1m&limit=${limit}`,
  )
}

// ── 사용자 ────────────────────────────────────────────────────

export function mockLogin(email: string, nickname?: string): Promise<MockLoginResult> {
  return request<MockLoginResult>('/api/users/mock-login', {
    method: 'POST',
    body: { email, nickname: nickname || undefined },
  })
}

export function fetchWatchlist(token: string): Promise<WatchlistItem[]> {
  return request<WatchlistItem[]>('/api/users/me/watchlist', { token })
}

export function addToWatchlist(token: string, symbol: string): Promise<WatchlistItem> {
  return request<WatchlistItem>(`/api/users/me/watchlist/${symbol}`, {
    method: 'POST',
    token,
  })
}

export function removeFromWatchlist(token: string, symbol: string): Promise<void> {
  return request<void>(`/api/users/me/watchlist/${symbol}`, { method: 'DELETE', token })
}

// ── 거래 (토큰 필요) ──────────────────────────────────────────

export function fetchAccount(token: string): Promise<Account> {
  return request<Account>('/api/trading/account', { token })
}

export function fetchPositions(token: string): Promise<Position[]> {
  return request<Position[]>('/api/trading/positions', { token })
}

export function fetchOrders(token: string, status?: OrderStatus): Promise<Order[]> {
  const query = status ? `?status=${status}` : ''
  return request<Order[]>(`/api/trading/orders${query}`, { token })
}

export function placeOrder(
  token: string,
  idempotencyKey: string,
  order: PlaceOrderRequest,
): Promise<Order> {
  return request<Order>('/api/trading/orders', {
    method: 'POST',
    token,
    idempotencyKey,
    body: order,
  })
}

export function fetchExecutions(token: string): Promise<Execution[]> {
  return request<Execution[]>('/api/trading/executions', { token })
}

export function fetchPortfolio(token: string): Promise<Portfolio> {
  return request<Portfolio>('/api/trading/portfolio', { token })
}

export function cancelOrder(token: string, orderId: number): Promise<Order> {
  return request<Order>(`/api/trading/orders/${orderId}`, { method: 'DELETE', token })
}
