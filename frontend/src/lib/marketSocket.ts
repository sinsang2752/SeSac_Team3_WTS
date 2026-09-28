import type { OrderBookSnapshot, PriceTick } from './types'

/**
 * /ws/market 클라이언트. (CLAUDE.md §25)
 *
 * 끊기면 지수 백오프로 재연결하고, 재연결 후에는 기존 구독을 복구한다.
 */

export type SocketStatus = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'closed'

interface Handlers {
  onStatus: (status: SocketStatus) => void
  onPrice: (price: PriceTick) => void
  onOrderBook: (orderBook: OrderBookSnapshot) => void
}

const INITIAL_RETRY_DELAY_MS = 500
const MAX_RETRY_DELAY_MS = 10_000

function endpoint(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/market`
}

export class MarketSocket {
  private readonly handlers: Handlers
  private socket: WebSocket | null = null
  private readonly symbols = new Set<string>()
  private retryDelayMs = INITIAL_RETRY_DELAY_MS
  private retryTimer: number | null = null
  /** 사용자가 의도적으로 끊은 경우에는 재연결하지 않는다. */
  private closedByUs = false

  constructor(handlers: Handlers) {
    this.handlers = handlers
  }

  connect(): void {
    if (this.socket && this.socket.readyState <= WebSocket.OPEN) return

    this.closedByUs = false
    this.handlers.onStatus(this.retryDelayMs === INITIAL_RETRY_DELAY_MS ? 'connecting' : 'reconnecting')

    const socket = new WebSocket(endpoint())
    this.socket = socket

    socket.onopen = () => {
      this.retryDelayMs = INITIAL_RETRY_DELAY_MS
      this.handlers.onStatus('open')
      // 재연결이라면 끊기기 전 구독을 되살린다.
      if (this.symbols.size > 0) this.send('SUBSCRIBE', [...this.symbols])
    }

    socket.onmessage = (event) => this.dispatch(event.data)

    socket.onclose = () => {
      this.socket = null
      if (this.closedByUs) {
        this.handlers.onStatus('closed')
        return
      }
      this.handlers.onStatus('reconnecting')
      this.scheduleReconnect()
    }

    // onerror 직후 onclose가 이어지므로 재연결은 onclose 한 곳에서만 처리한다.
    socket.onerror = () => socket.close()
  }

  disconnect(): void {
    this.closedByUs = true
    if (this.retryTimer !== null) {
      window.clearTimeout(this.retryTimer)
      this.retryTimer = null
    }
    this.socket?.close()
    this.socket = null
    this.handlers.onStatus('closed')
  }

  subscribe(symbols: string[]): void {
    symbols.forEach((symbol) => this.symbols.add(symbol))
    this.send('SUBSCRIBE', symbols)
  }

  unsubscribe(symbols: string[]): void {
    symbols.forEach((symbol) => this.symbols.delete(symbol))
    this.send('UNSUBSCRIBE', symbols)
  }

  private send(type: 'SUBSCRIBE' | 'UNSUBSCRIBE', symbols: string[]): void {
    if (symbols.length === 0) return
    // 아직 연결 전이면 보내지 않는다. onopen에서 전체 구독을 다시 보낸다.
    if (this.socket?.readyState !== WebSocket.OPEN) return
    this.socket.send(JSON.stringify({ type, symbols }))
  }

  private dispatch(raw: unknown): void {
    if (typeof raw !== 'string') return
    let message: { type?: string }
    try {
      message = JSON.parse(raw)
    } catch {
      return
    }

    switch (message.type) {
      case 'PRICE':
        this.handlers.onPrice(message as unknown as PriceTick)
        break
      case 'ORDERBOOK':
        this.handlers.onOrderBook(message as unknown as OrderBookSnapshot)
        break
      default:
        // SUBSCRIBED / ERROR 는 상태 표시에 쓰지 않는다. 필요해지면 여기서 처리한다.
        break
    }
  }

  private scheduleReconnect(): void {
    if (this.retryTimer !== null) return
    const delay = this.retryDelayMs
    this.retryDelayMs = Math.min(delay * 2, MAX_RETRY_DELAY_MS)
    this.retryTimer = window.setTimeout(() => {
      this.retryTimer = null
      this.connect()
    }, delay)
  }
}
