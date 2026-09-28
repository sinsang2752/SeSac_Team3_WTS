import { useToastStore } from '../stores/toastStore'

/** 화면 하단 알림 묶음. 앱 전체에 하나만 둔다. */
export function Toasts() {
  const toasts = useToastStore((state) => state.toasts)
  if (toasts.length === 0) return null

  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast${toast.tone === 'error' ? ' toast--error' : ''}`}>
          {toast.message}
        </div>
      ))}
    </div>
  )
}
