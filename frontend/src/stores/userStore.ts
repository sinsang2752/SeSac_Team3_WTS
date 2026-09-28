import { create } from 'zustand'

import type { MockLoginResult } from '../lib/types'

/**
 * 로그인 세션. (CLAUDE.md §29 – userStore)
 *
 * <p>토큰을 localStorage에 둔다. 새로고침으로 로그인이 풀리면 주문 화면을 쓸 수 없기 때문이다.
 * XSS가 있으면 토큰이 새어 나가는 저장 위치이지만, MVP의 Mock 토큰은 가상 계좌에만 접근
 * 가능하고 실제 자산과 무관하다. 실제 인증(Cognito)으로 바꿀 때 다시 볼 자리다 (§47 Phase 8).
 */
export interface Session {
  userId: string
  email: string
  nickname: string
  accessToken: string
  /** epoch milliseconds. */
  expiresAt: number
}

const STORAGE_KEY = 'wts.session'

function load(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    // 만료된 세션을 들고 있으면 모든 요청이 401로 돌아온다. 미리 버린다.
    if (!session.accessToken || session.expiresAt <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return session
  } catch {
    // 형식이 바뀐 낡은 값이 남아 있는 경우다.
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

function save(session: Session | null) {
  if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  else localStorage.removeItem(STORAGE_KEY)
}

interface UserState {
  session: Session | null
  login: (result: MockLoginResult) => void
  logout: () => void
}

export const useUserStore = create<UserState>((set) => ({
  session: load(),

  login: (result) => {
    const session: Session = {
      userId: result.userId,
      email: result.email,
      nickname: result.nickname,
      accessToken: result.accessToken,
      expiresAt: Date.now() + result.expiresInSeconds * 1000,
    }
    save(session)
    set({ session })
  },

  logout: () => {
    save(null)
    set({ session: null })
  },
}))
