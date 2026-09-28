import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { addToWatchlist, fetchWatchlist, removeFromWatchlist } from '../../lib/api'
import type { WatchlistItem } from '../../lib/types'
import { useUserStore } from '../../stores/userStore'

/** 관심종목. (CLAUDE.md §24, §29) */
export function useWatchlist() {
  const token = useUserStore((state) => state.session?.accessToken ?? null)
  return useQuery({
    queryKey: ['watchlist'],
    queryFn: () => fetchWatchlist(token as string),
    enabled: token !== null,
  })
}

/**
 * 담기/빼기를 한 동작으로 다룬다.
 *
 * <p>서버가 두 요청 모두 멱등하게 처리하므로(이미 담긴 종목 추가, 없는 종목 삭제)
 * 화면은 결과 상태만 신경 쓰면 된다.
 */
export function useToggleWatchlist() {
  const token = useUserStore((state) => state.session?.accessToken ?? null)
  const queryClient = useQueryClient()

  return useMutation<WatchlistItem | void, Error, { symbol: string; watched: boolean }>({
    mutationFn: ({ symbol, watched }) =>
      watched
        ? removeFromWatchlist(token as string, symbol)
        : addToWatchlist(token as string, symbol),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['watchlist'] })
    },
  })
}
