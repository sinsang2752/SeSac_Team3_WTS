import { useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'

import { fetchStocks } from '../../lib/api'

/**
 * 종목코드 → 종목명.
 *
 * <p>거래 API는 종목코드만 돌려준다. 이름은 종목 마스터(market-service)에 있다.
 * 이미 받아 둔 목록을 쓰므로 표를 그릴 때 추가 요청이 생기지 않는다.
 * 아직 목록이 없으면 종목코드를 그대로 보여준다. 빈칸을 두지 않는다.
 */
export function useStockName(): (symbol: string) => string {
  const { data: stocks } = useQuery({
    queryKey: ['stocks'],
    queryFn: () => fetchStocks(),
    staleTime: 5 * 60 * 1000,
  })

  return useCallback(
    (symbol: string) => stocks?.find((stock) => stock.symbol === symbol)?.name ?? symbol,
    [stocks],
  )
}
