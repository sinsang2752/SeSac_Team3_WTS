import { useEffect, useRef } from 'react'

import { useWebSocketStore } from '../../stores/websocketStore'

/**
 * 시세 WebSocket에 연결하고 주어진 종목만 구독한다. (CLAUDE.md §25, §29, §57.3)
 *
 * <p>목록이 바뀌면 새 종목만 구독하고 <b>빠진 종목은 구독을 푼다</b>. 검색어를 바꿀 때마다
 * 구독이 쌓이면 서버가 화면에 없는 종목까지 계속 보낸다.
 *
 * 연결 전에 구독을 요청해도 괜찮다. 소켓이 종목을 기억했다가 연결되는 순간 한꺼번에 보낸다.
 */
export function useMarketStream(symbols: readonly string[]): void {
  const connect = useWebSocketStore((state) => state.connect)
  const disconnect = useWebSocketStore((state) => state.disconnect)
  const subscribe = useWebSocketStore((state) => state.subscribe)
  const unsubscribe = useWebSocketStore((state) => state.unsubscribe)
  const subscribed = useRef<Set<string>>(new Set())

  useEffect(() => {
    connect()
    return () => disconnect()
  }, [connect, disconnect])

  // 배열 참조가 아니라 내용이 바뀔 때만 다시 계산한다.
  const key = [...new Set(symbols)].sort().join(',')
  useEffect(() => {
    const next = new Set(key.length > 0 ? key.split(',') : [])
    const previous = subscribed.current
    const removed = [...previous].filter((symbol) => !next.has(symbol))
    const added = [...next].filter((symbol) => !previous.has(symbol))
    if (removed.length > 0) unsubscribe(removed)
    if (added.length > 0) subscribe(added)
    subscribed.current = next
  }, [key, subscribe, unsubscribe])

  // 화면을 떠나면 다음 연결 때 되살아나지 않게 소켓에 남은 구독도 지운다.
  useEffect(
    () => () => {
      unsubscribe([...subscribed.current])
      subscribed.current = new Set()
    },
    [unsubscribe],
  )
}
