import { useCallback, useMemo } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { fetchStocksBySymbols } from '../../lib/api'

/**
 * 종목코드 → 종목명.
 *
 * <p>거래 API는 종목코드만 돌려준다. 이름은 종목 마스터(market-service)에 있다.
 * 전 종목(2,700개)을 받아 두지 않고, 표에 보이는 종목코드만 한 번에 묻는다 (CLAUDE.md §57.3).
 * 아직 이름을 모르면 종목코드를 그대로 보여준다. 빈칸을 두지 않는다.
 */
export function useStockNames(symbols: readonly string[]): (symbol: string) => string {
  // 순서와 중복이 달라도 같은 요청이다. 같은 캐시를 쓰게 정규화한다.
  const unique = [...new Set(symbols)].sort()

  const { data: stocks } = useQuery({
    queryKey: ['stockNames', unique],
    queryFn: () => fetchStocksBySymbols(unique),
    enabled: unique.length > 0,
    // 종목명은 하루에 한 번 바뀔까 말까다.
    staleTime: 60 * 60 * 1000,
    // 행이 하나 늘 때 기존 이름이 종목코드로 깜빡이지 않게 이전 결과를 들고 있는다.
    placeholderData: keepPreviousData,
  })

  const names = useMemo(
    () => new Map((stocks ?? []).map((stock) => [stock.symbol, stock.name])),
    [stocks],
  )

  return useCallback((symbol: string) => names.get(symbol) ?? symbol, [names])
}
