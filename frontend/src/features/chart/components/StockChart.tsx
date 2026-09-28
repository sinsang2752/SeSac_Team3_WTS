import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  CandlestickSeries,
  HistogramSeries,
  createChart,
  type CandlestickData,
  type HistogramData,
  type IChartApi,
  type ISeriesApi,
  type MouseEventParams,
  type UTCTimestamp,
} from 'lightweight-charts'

import { PanelState } from '../../../components/PanelState'
import { fetchCandles } from '../../../lib/api'
import {
  formatPrice,
  formatSeoulHourMinute,
  formatSeoulTimeFromEpoch,
  formatVolume,
} from '../../../lib/format'
import type { Candle } from '../../../lib/types'
import { useMarketStore } from '../../../stores/marketStore'

interface Props {
  symbol: string
}

/** 차트 색은 tokens.css의 등락 의미색과 같아야 한다 (ui-requirements §3). */
const UP = '#c0364f'
const DOWN = '#2457c5'
const GRID = '#e2e8f0'
const AXIS_TEXT = '#475569'

function toCandle(candle: Candle): CandlestickData {
  return {
    // Lightweight Charts는 초 단위 UNIX 시각을 받는다.
    time: (Date.parse(candle.openTime) / 1000) as UTCTimestamp,
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
  }
}

function toVolume(candle: Candle): HistogramData {
  return {
    time: (Date.parse(candle.openTime) / 1000) as UTCTimestamp,
    value: candle.volume,
    color: candle.close >= candle.open ? `${UP}44` : `${DOWN}44`,
  }
}

/** 마우스를 올린 봉의 OHLCV. 올리지 않았으면 마지막 봉을 보여준다. */
interface Readout {
  open: number
  high: number
  low: number
  close: number
  volume: number
}

/**
 * 1분봉 차트. (CLAUDE.md §4 – TradingView Lightweight Charts, §22, ui-requirements §5.3)
 *
 * <p>과거 봉은 REST로 한 번 읽고, 진행 중인 봉은 WebSocket 현재가로 갱신한다.
 * 봉 전체를 매초 다시 내려받지 않기 위해서다.
 *
 * <p>일봉·주봉·월봉 전환은 만들지 않는다. 서버가 1분봉만 집계한다.
 * 동작하지 않는 기간 버튼을 두지 않는다.
 */
export function StockChart({ symbol }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const candleRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const volumeRef = useRef<ISeriesApi<'Histogram'> | null>(null)
  const [hovered, setHovered] = useState<Readout | null>(null)

  const { data: candles, isPending, isError } = useQuery({
    queryKey: ['candles', symbol],
    queryFn: () => fetchCandles(symbol),
    // 1분봉이므로 그보다 자주 받을 이유가 없다. 진행 중인 봉은 시세로 덧그린다.
    refetchInterval: 60_000,
  })

  const tick = useMarketStore((state) => state.prices[symbol])

  // 차트는 DOM에 직접 그리는 외부 라이브러리다. 마운트 시 한 번만 만든다.
  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const chart = createChart(container, {
      autoSize: true,
      layout: { background: { color: 'transparent' }, textColor: AXIS_TEXT, fontSize: 11 },
      /*
       * 봉의 시각은 UTC로 저장하고 화면에는 한국 시각으로 보여준다 (CLAUDE.md §43).
       * 라이브러리 기본값은 UTC라서 09:15 봉이 00:15로 찍힌다.
       */
      localization: {
        locale: 'ko-KR',
        timeFormatter: (time: unknown) => formatSeoulTimeFromEpoch(time as number),
      },
      grid: { vertLines: { color: GRID, style: 1 }, horzLines: { color: GRID, style: 1 } },
      rightPriceScale: { borderColor: GRID, scaleMargins: { top: 0.08, bottom: 0.26 } },
      timeScale: {
        borderColor: GRID,
        timeVisible: true,
        secondsVisible: false,
        tickMarkFormatter: (time: unknown) => formatSeoulHourMinute(time as number),
      },
      crosshair: { mode: 1 },
    })

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: UP,
      downColor: DOWN,
      borderUpColor: UP,
      borderDownColor: DOWN,
      wickUpColor: UP,
      wickDownColor: DOWN,
      priceFormat: { type: 'price', precision: 0, minMove: 1 },
      // 현재가 선과 축 라벨은 시리즈가 그린다. 따로 그리면 라벨이 겹친다.
      priceLineVisible: true,
      priceLineStyle: 2,
      priceLineWidth: 1,
    })

    // 거래량은 같은 창의 아래 22%를 쓴다. 가격 축과 눈금을 공유하지 않는다.
    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceScaleId: 'volume',
      priceFormat: { type: 'volume' },
      lastValueVisible: false,
      priceLineVisible: false,
    })
    chart
      .priceScale('volume')
      .applyOptions({ scaleMargins: { top: 0.78, bottom: 0 }, visible: false })

    // 커서를 올린 봉의 OHLCV를 위쪽 범례에 띄운다.
    const onCrosshair = (param: MouseEventParams) => {
      const candle = param.seriesData.get(candleSeries) as CandlestickData | undefined
      const volume = param.seriesData.get(volumeSeries) as HistogramData | undefined
      if (!candle) {
        setHovered(null)
        return
      }
      setHovered({
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
        volume: volume?.value ?? 0,
      })
    }
    chart.subscribeCrosshairMove(onCrosshair)

    chartRef.current = chart
    candleRef.current = candleSeries
    volumeRef.current = volumeSeries

    return () => {
      chart.unsubscribeCrosshairMove(onCrosshair)
      chart.remove()
      chartRef.current = null
      candleRef.current = null
      volumeRef.current = null
    }
  }, [])

  // 종목이 바뀌거나 봉을 새로 받으면 통째로 다시 그린다.
  useEffect(() => {
    if (!candleRef.current || !volumeRef.current || !candles) return
    candleRef.current.setData(candles.map(toCandle))
    volumeRef.current.setData(candles.map(toVolume))
    chartRef.current?.timeScale().fitContent()
    setHovered(null)
  }, [candles])

  // 진행 중인 봉을 현재가로 갱신한다.
  useEffect(() => {
    const series = candleRef.current
    if (!series || !tick || !candles || candles.length === 0) return
    const last = candles[candles.length - 1]
    const openTime = Date.parse(last.openTime)
    // 현재가가 마지막 봉보다 뒤의 분에 속하면 새 봉이 열려야 한다.
    // 그건 다음 조회가 가져온다. 여기서는 마지막 봉만 덧그린다.
    if (Date.parse(tick.timestamp) < openTime) return

    series.update({
      time: (openTime / 1000) as UTCTimestamp,
      open: last.open,
      high: Math.max(last.high, tick.price),
      low: Math.min(last.low, tick.price),
      close: tick.price,
    })
  }, [tick, candles])

  const last = candles && candles.length > 0 ? candles[candles.length - 1] : null
  const readout: Readout | null =
    hovered ??
    (last
      ? { open: last.open, high: last.high, low: last.low, close: last.close, volume: last.volume }
      : null)

  const empty = !isPending && !isError && candles?.length === 0

  return (
    <>
      <div className="chart__toolbar">
        <span className="chart__label">
          차트
          <span className="chart__interval">1분봉</span>
        </span>
        {readout && (
          <span className="numeric">
            시 {formatPrice(readout.open)} · 고 {formatPrice(readout.high)} · 저{' '}
            {formatPrice(readout.low)} · 종 {formatPrice(readout.close)} · 거래량{' '}
            {formatVolume(readout.volume)}
          </span>
        )}
      </div>

      {/*
        캔버스는 언제나 화면 크기를 가져야 한다. 숨기면 폭이 0이 되고, 그 상태에서 맞춘
        시간축이 다시 보일 때까지 어긋난 채로 남는다. 상태 표시는 위에 덮는다.
      */}
      <div className="chart">
        <div className="chart__canvas" ref={containerRef} />
        {isPending && (
          <div className="chart__state">
            <PanelState tone="loading">차트를 불러오는 중…</PanelState>
          </div>
        )}
        {isError && (
          <div className="chart__state">
            <PanelState tone="error" hint="잠시 후 다시 확인해 주세요.">
              차트를 불러오지 못했습니다.
            </PanelState>
          </div>
        )}
        {empty && (
          <div className="chart__state">
            <PanelState hint="1분 뒤 첫 봉이 만들어집니다.">아직 표시할 차트가 없습니다.</PanelState>
          </div>
        )}
      </div>
    </>
  )
}
