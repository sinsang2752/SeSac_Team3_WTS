import { useCallback, useEffect, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  cancelOrder,
  fetchAccount,
  fetchExecutions,
  fetchOrders,
  fetchPositions,
  placeOrder,
} from '../../lib/api'
import { formatQuantity } from '../../lib/format'
import type { Order, PlaceOrderRequest } from '../../lib/types'
import { useStockNames } from '../market/useStockNames'
import { useToastStore } from '../../stores/toastStore'
import { useUserStore } from '../../stores/userStore'

/**
 * 계좌 · 포지션 · 주문 조회. (CLAUDE.md §29 – TanStack Query)
 *
 * <p>서버 상태는 전부 TanStack Query가 들고 있다. 같은 값을 Zustand에 복사하지 않는다.
 * 그래서 §29가 후보로 든 tradingStore는 아직 없다. 주문 폼의 입력값은 컴포넌트 로컬 상태다.
 */
function useToken(): string | null {
  return useUserStore((state) => state.session?.accessToken ?? null)
}

export function useAccount() {
  const token = useToken()
  return useQuery({
    queryKey: ['account'],
    queryFn: () => fetchAccount(token as string),
    enabled: token !== null,
  })
}

export function usePositions() {
  const token = useToken()
  return useQuery({
    queryKey: ['positions'],
    queryFn: () => fetchPositions(token as string),
    enabled: token !== null,
  })
}

/**
 * 미체결 주문. 지정가 주문이 여기 쌓인다.
 *
 * <p>서버가 시세 이벤트를 받아 자동으로 체결하므로(Phase 4) 클라이언트가 모르는 사이에
 * 목록이 줄어든다. 미체결 주문이 있을 때만 주기적으로 다시 읽는다.
 * 거래 전용 WebSocket을 따로 만드는 것보다 이 편이 MVP에 맞다.
 */
export function useOpenOrders() {
  const token = useToken()
  return useQuery({
    queryKey: ['orders', 'ACCEPTED'],
    queryFn: () => fetchOrders(token as string, 'ACCEPTED'),
    enabled: token !== null,
    refetchInterval: (query) => ((query.state.data?.length ?? 0) > 0 ? 3000 : false),
  })
}

/** 체결 내역. 체결가와 실현손익은 여기에만 있다. */
export function useExecutions() {
  const token = useToken()
  return useQuery({
    queryKey: ['executions'],
    queryFn: () => fetchExecutions(token as string),
    enabled: token !== null,
  })
}

/** 전체 주문 내역. 거절된 주문도 이유와 함께 들어 있다. */
export function useOrderHistory() {
  const token = useToken()
  return useQuery({
    queryKey: ['orders', 'ALL'],
    queryFn: () => fetchOrders(token as string),
    enabled: token !== null,
  })
}

/** 체결되면 예수금·포지션·체결내역이 함께 바뀐다. 한 번에 다시 읽는다. */
export function useRefreshTrading() {
  const queryClient = useQueryClient()
  return useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['account'] })
    void queryClient.invalidateQueries({ queryKey: ['positions'] })
    void queryClient.invalidateQueries({ queryKey: ['orders'] })
    void queryClient.invalidateQueries({ queryKey: ['executions'] })
    void queryClient.invalidateQueries({ queryKey: ['portfolio'] })
  }, [queryClient])
}

/**
 * 미체결 주문이 서버에서 체결되면 나머지 화면도 같이 갱신하고, 체결 사실을 알린다.
 *
 * <p>{@link useOpenOrders}의 주기 조회가 목록의 변화를 먼저 잡는다.
 * 목록이 달라진 순간에만 나머지를 무효화하므로 불필요한 조회가 생기지 않는다.
 *
 * <p>체결 알림은 <b>체결 기록이 생겼을 때만</b> 낸다. 주문 접수는 체결이 아니므로
 * 여기서 알리지 않는다. 접수 안내는 주문 패널이 맡는다 (ui-requirements §5.5).
 */
export function useFillWatcher() {
  const { data: openOrders } = useOpenOrders()
  const { data: executions } = useExecutions()
  const refresh = useRefreshTrading()
  const pushToast = useToastStore((state) => state.push)
  // 새 체결의 종목명이 아직 없으면 종목코드로 알린다. 알림을 늦추지 않는다.
  const nameOf = useStockNames((executions ?? []).map((execution) => execution.symbol))

  const openOrderKey = (openOrders ?? []).map((order) => order.orderId).join(',')
  const previousKey = useRef<string | null>(null)

  useEffect(() => {
    if (previousKey.current !== null && previousKey.current !== openOrderKey) {
      refresh()
    }
    previousKey.current = openOrderKey
  }, [openOrderKey, refresh])

  /** 이미 알린 체결. null이면 아직 첫 조회 결과를 받지 못한 상태다. */
  const announced = useRef<Set<number> | null>(null)

  useEffect(() => {
    if (!executions) return
    // 화면에 처음 들어왔을 때 과거 체결을 전부 알리면 안 된다. 기준선만 잡는다.
    if (announced.current === null) {
      announced.current = new Set(executions.map((execution) => execution.executionId))
      return
    }
    const seen = announced.current
    for (const execution of executions) {
      if (seen.has(execution.executionId)) continue
      seen.add(execution.executionId)
      pushToast(
        `${nameOf(execution.symbol)} ${formatQuantity(execution.quantity)}가 모의 체결되었습니다.`,
      )
    }
  }, [executions, nameOf, pushToast])
}

export function usePlaceOrder() {
  const token = useToken()
  const refresh = useRefreshTrading()
  return useMutation<Order, Error, { idempotencyKey: string; order: PlaceOrderRequest }>({
    mutationFn: ({ idempotencyKey, order }) =>
      placeOrder(token as string, idempotencyKey, order),
    onSuccess: refresh,
  })
}

export function useCancelOrder() {
  const token = useToken()
  const refresh = useRefreshTrading()
  return useMutation<Order, Error, number>({
    mutationFn: (orderId) => cancelOrder(token as string, orderId),
    onSuccess: refresh,
  })
}
