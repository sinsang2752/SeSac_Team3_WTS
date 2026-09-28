import type { OrderStatus } from '../../lib/types'

/**
 * 서버 주문 상태 → 사용자 표기.
 * (CLAUDE.md §9.6, docs/learnstock/ui-requirements.md §6)
 *
 * <p>상태 이름을 화면에서 새로 만들지 않는다. 여기 있는 다섯 가지가 서버가 주는 전부다.
 * `취소 요청 중`처럼 응답을 기다리는 표시는 주문 상태가 아니라 버튼의 일시 상태로 다룬다.
 */
export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  RECEIVED: '접수됨',
  // 검증까지 끝났지만 아직 체결 판정 전이다. 사용자에게는 접수와 같은 단계다.
  VALIDATED: '접수됨',
  ACCEPTED: '체결 대기',
  FILLED: '체결 완료',
  CANCELLED: '취소됨',
  REJECTED: '거절됨',
}

/** 배지 색. 체결 완료는 손익 방향을 뜻하지 않으므로 등락색을 쓰지 않는다. */
export const ORDER_STATUS_TONE: Record<OrderStatus, string> = {
  RECEIVED: '',
  VALIDATED: '',
  ACCEPTED: ' badge--waiting',
  FILLED: ' badge--done',
  CANCELLED: '',
  REJECTED: ' badge--error',
}

/** 서버의 ErrorCode를 화면 문구로 바꾼다 (CLAUDE.md §39). */
export function rejectReasonLabel(reason: string | undefined): string {
  switch (reason) {
    case 'INSUFFICIENT_BALANCE':
      return '주문가능금액 부족'
    case 'INSUFFICIENT_POSITION':
      return '매도가능수량 부족'
    case 'MARKET_PRICE_STALE':
      return '시세 지연'
    case 'MARKET_PRICE_UNAVAILABLE':
      return '시세 없음'
    case 'INVALID_ORDER_STATE':
      return '처리할 수 없는 주문 상태'
    default:
      // 서버가 새 코드를 추가해도 화면이 빈칸이 되지 않게 한다.
      return reason ?? '사유 미상'
  }
}
