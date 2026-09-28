import type { ReactNode } from 'react'

interface Props {
  /** 비워 두면 머리말 없이 본문만 그린다. */
  title?: ReactNode
  /** 제목 오른쪽의 보조 정보. */
  meta?: ReactNode
  className?: string
  children: ReactNode
}

/** 카드형 패널. (docs/learnstock/ui-requirements.md §4 – DataPanel) */
export function Panel({ title, meta, className, children }: Props) {
  return (
    <section className={`panel${className ? ` ${className}` : ''}`}>
      {title !== undefined && (
        <div className="panel__head">
          <h2 className="panel__title">{title}</h2>
          {meta}
        </div>
      )}
      {children}
    </section>
  )
}
