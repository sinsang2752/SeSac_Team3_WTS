import { useEffect, useRef, type ReactNode } from 'react'

interface Props {
  open: boolean
  title: string
  /** 제출 중에는 닫히지 않는다. 취소로 오해하지 않게 한다. */
  busy?: boolean
  onClose: () => void
  children: ReactNode
  note?: ReactNode
  actions: ReactNode
}

/**
 * 확인 오버레이. (docs/learnstock/ui-requirements.md §5.5)
 *
 * <p>브라우저의 {@code <dialog>}를 쓴다. 포커스 가두기와 Esc 처리를 직접 구현하면
 * 접근성 결함이 생기기 쉬운 자리다.
 */
export function Modal({ open, title, busy, onClose, children, note, actions }: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      className="modal"
      ref={ref}
      aria-busy={busy || undefined}
      // Esc 키도 여기로 들어온다. 처리 중에는 막는다.
      onCancel={(event) => {
        event.preventDefault()
        if (!busy) onClose()
      }}
    >
      <div className="modal__head">
        <h2 className="modal__title">{title}</h2>
        <button type="button" className="modal__close" aria-label="닫기" disabled={busy} onClick={onClose}>
          ×
        </button>
      </div>
      <div className="modal__body">{children}</div>
      {note && <p className="modal__note">{note}</p>}
      <div className="modal__actions">{actions}</div>
    </dialog>
  )
}
