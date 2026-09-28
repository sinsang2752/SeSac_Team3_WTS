import { useEffect } from 'react'

import { useWebSocketStore } from '../../stores/websocketStore'

/**
 * 시세 WebSocket에 연결하고 주어진 종목을 구독한다. (CLAUDE.md §25, §29)
 *
 * 연결 전에 구독을 요청해도 괜찮다. 소켓이 종목을 기억했다가 연결되는 순간 한꺼번에 보낸다.
 */
export function useMarketStream(symbols: string[]): void {
  const connect = useWebSocketStore((state) => state.connect)
  const disconnect = useWebSocketStore((state) => state.disconnect)
  const subscribe = useWebSocketStore((state) => state.subscribe)

  useEffect(() => {
    connect()
    return () => disconnect()
  }, [connect, disconnect])

  // 배열 참조가 아니라 내용이 바뀔 때만 다시 구독한다.
  const key = symbols.join(',')
  useEffect(() => {
    if (key.length > 0) subscribe(key.split(','))
  }, [key, subscribe])
}
