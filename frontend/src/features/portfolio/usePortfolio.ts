import { useQuery } from '@tanstack/react-query'

import { fetchPortfolio } from '../../lib/api'
import { useUserStore } from '../../stores/userStore'

/**
 * 포트폴리오. (CLAUDE.md §24, §29)
 *
 * <p>서버가 한 시점의 시세로 계산한 스냅샷이다. 매초 바뀌는 값을 매초 조회할 수는 없으므로
 * 10초마다 다시 읽는다. 실시간이 필요한 곳은 거래 화면의 보유종목 표다.
 */
export function usePortfolio() {
  const token = useUserStore((state) => state.session?.accessToken ?? null)
  return useQuery({
    queryKey: ['portfolio'],
    queryFn: () => fetchPortfolio(token as string),
    enabled: token !== null,
    refetchInterval: 10_000,
  })
}
