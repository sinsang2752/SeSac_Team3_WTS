import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { useUserStore } from '../stores/userStore'

/**
 * 로그인하지 않았으면 /login으로 보낸다.
 *
 * <p>진짜 보호는 Gateway가 한다. 여기서 막는 것은 토큰 없이 호출해 401만 잔뜩 받는 화면을
 * 보여주지 않기 위한 것이다 (CLAUDE.md §6.1).
 */
export function RequireSession({ children }: { children: ReactNode }) {
  const session = useUserStore((state) => state.session)
  const location = useLocation()

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <>{children}</>
}
