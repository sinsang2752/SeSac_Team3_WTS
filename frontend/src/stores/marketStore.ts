import { create } from 'zustand'

import type { OrderBookSnapshot, PriceTick } from '../lib/types'

/** 실시간 시세 상태. (CLAUDE.md §29 – marketStore) */
interface MarketState {
  prices: Record<string, PriceTick>
  orderBooks: Record<string, OrderBookSnapshot>
  /** 사용자가 보고 있는 종목. */
  selectedSymbol: string | null
  applyPrice: (price: PriceTick) => void
  applyOrderBook: (orderBook: OrderBookSnapshot) => void
  selectSymbol: (symbol: string) => void
}

export const useMarketStore = create<MarketState>((set) => ({
  prices: {},
  orderBooks: {},
  selectedSymbol: null,

  applyPrice: (price) =>
    set((state) => ({ prices: { ...state.prices, [price.symbol]: price } })),

  applyOrderBook: (orderBook) =>
    set((state) => ({ orderBooks: { ...state.orderBooks, [orderBook.symbol]: orderBook } })),

  selectSymbol: (symbol) => set({ selectedSymbol: symbol }),
}))
