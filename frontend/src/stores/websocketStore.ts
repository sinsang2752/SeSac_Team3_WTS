import { create } from 'zustand'

import { MarketSocket, type SocketStatus } from '../lib/marketSocket'
import { useMarketStore } from './marketStore'

/** WebSocket 연결 상태. (CLAUDE.md §29 – websocketStore) */
interface WebSocketState {
  status: SocketStatus
  connect: () => void
  disconnect: () => void
  subscribe: (symbols: string[]) => void
  unsubscribe: (symbols: string[]) => void
}

/**
 * 소켓 인스턴스는 스토어 상태가 아니라 모듈 스코프에 둔다.
 * 렌더링에 쓰이지 않는 값이라 상태에 넣으면 불필요한 리렌더만 유발한다.
 */
let socket: MarketSocket | null = null

export const useWebSocketStore = create<WebSocketState>((set) => ({
  status: 'idle',

  connect: () => {
    if (socket === null) {
      socket = new MarketSocket({
        onStatus: (status) => set({ status }),
        onPrice: (price) => useMarketStore.getState().applyPrice(price),
        onOrderBook: (orderBook) => useMarketStore.getState().applyOrderBook(orderBook),
      })
    }
    socket.connect()
  },

  disconnect: () => socket?.disconnect(),

  subscribe: (symbols) => socket?.subscribe(symbols),

  unsubscribe: (symbols) => socket?.unsubscribe(symbols),
}))
