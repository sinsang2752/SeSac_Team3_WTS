import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'

import { Panel } from '../../components/Panel'
import { ApiRequestError, mockLogin } from '../../lib/api'
import type { MockLoginResult } from '../../lib/types'
import { useUserStore } from '../../stores/userStore'

/** MVP 기본 진입로. 팀이 공유하는 연습 계정이다 (README). */
const DEMO_EMAIL = 'demo@wts.local'
const DEMO_NICKNAME = '데모투자자'

/**
 * Mock Login. (CLAUDE.md §6.4, §47 Phase 1, ui-requirements §8)
 *
 * <p>비밀번호가 없다. 이메일로 사용자를 찾고 없으면 만든다. 실제 인증은 Phase 8이다.
 *
 * <p>초기 지급 금액은 서버 설정값이라 여기에 적지 않는다 (CLAUDE.md §9.1).
 * 화면이 확정된 금액을 약속하면 설정을 바꿨을 때 거짓말이 된다.
 */
export function LoginPanel({ onSuccess }: { onSuccess?: () => void }) {
  const [email, setEmail] = useState('')
  const [nickname, setNickname] = useState('')
  const login = useUserStore((state) => state.login)
  const queryClient = useQueryClient()

  const mutation = useMutation<MockLoginResult, Error, { email: string; nickname: string }>({
    mutationFn: (credentials) => mockLogin(credentials.email, credentials.nickname),
    onSuccess: (result) => {
      login(result)
      // 이전 사용자의 계좌·주문이 화면에 남으면 안 된다.
      void queryClient.invalidateQueries()
      onSuccess?.()
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate({ email: email.trim(), nickname: nickname.trim() })
  }

  return (
    <Panel>
      <form className="login" onSubmit={submit}>
        <h2 className="login__title">투자를 배우는 첫걸음, 런스톡</h2>
        <p className="login__lead">가상 자금으로 국내 주식 거래를 경험해 보세요.</p>

        <label className="login__field">
          <span>이메일</span>
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="name@example.com"
            required
            maxLength={255}
            autoComplete="email"
          />
        </label>

        <label className="login__field">
          <span>닉네임 (선택)</span>
          <input
            type="text"
            value={nickname}
            onChange={(event) => setNickname(event.target.value)}
            placeholder="화면에 표시할 이름"
            maxLength={50}
          />
        </label>

        <button type="submit" className="btn btn--primary btn--block" disabled={mutation.isPending}>
          {mutation.isPending ? '계좌를 준비하는 중…' : '모의투자 시작하기'}
        </button>

        <button
          type="button"
          className="btn btn--block"
          disabled={mutation.isPending}
          onClick={() => mutation.mutate({ email: DEMO_EMAIL, nickname: DEMO_NICKNAME })}
        >
          데모 계정으로 시작하기
        </button>

        <p className="login__note">
          비밀번호가 없는 모의 로그인입니다. 처음 보는 이메일이면 계정과 가상 계좌가 함께
          만들어집니다.
        </p>

        {mutation.isError && (
          <p className="login__error" role="alert">
            {mutation.error instanceof ApiRequestError
              ? mutation.error.message
              : '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.'}
          </p>
        )}
      </form>
    </Panel>
  )
}
