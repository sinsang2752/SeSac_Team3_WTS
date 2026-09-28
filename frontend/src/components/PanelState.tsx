import type { ReactNode } from 'react'

interface Props {
  /** 'loading' | 'empty' | 'error' */
  tone?: 'loading' | 'empty' | 'error'
  children: ReactNode
  /** 본문 아래 한 줄 설명. 다음에 무엇을 하면 되는지 알려준다. */
  hint?: ReactNode
  /** 비어 있는 화면에서 이어갈 행동. */
  action?: ReactNode
}

/**
 * 로딩 · 빈 상태 · 오류를 한 자리에서 표현한다.
 *
 * <p>세 상태를 각각 다른 방식으로 그리면 화면마다 문구와 여백이 어긋난다.
 * (docs/learnstock/ui-requirements.md §4 – DataPanel)
 */
export function PanelState({ tone = 'empty', children, hint, action }: Props) {
  return (
    <div
      className={`state${tone === 'error' ? ' state--error' : ''}`}
      role={tone === 'error' ? 'alert' : undefined}
      aria-busy={tone === 'loading' || undefined}
    >
      <p style={{ margin: 0 }}>{children}</p>
      {hint && <p className="state__hint">{hint}</p>}
      {action && <div className="state__action">{action}</div>}
    </div>
  )
}
