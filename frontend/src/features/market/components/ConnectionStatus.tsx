import { useWebSocketStore } from '../../../stores/websocketStore'

/**
 * 시세 연결 상태. (CLAUDE.md §28, ui-requirements §4 – ConnectionStatus)
 *
 * <p>구현 용어(Kafka·Valkey·WebSocket)를 사용자에게 노출하지 않는다.
 * 끊긴 상태에서 실시간인 것처럼 보이지 않게 하는 것이 이 표시의 목적이다.
 *
 * <p>`시세 지연`과 `샘플 시세`는 아직 만들지 않았다. 지연 판정 임계값은 확정된 정책이 없고,
 * Mock/KIS 중 어느 쪽에서 시세가 오는지는 서버가 알려주지 않는다.
 * 없는 기준을 화면이 지어내는 대신 비워 둔다.
 */
const LABEL: Record<string, string> = {
  idle: '대기',
  connecting: '연결 중',
  open: '연결됨',
  reconnecting: '재연결 중',
  closed: '연결 끊김',
}

export function ConnectionStatus() {
  const status = useWebSocketStore((state) => state.status)

  return (
    <span className={`connection connection--${status}`}>
      <span className="connection__dot" aria-hidden="true" />
      시세 {LABEL[status] ?? status}
    </span>
  )
}
