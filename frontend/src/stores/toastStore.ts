import { create } from 'zustand'

/**
 * 짧은 알림. (docs/learnstock/ui-requirements.md §4 – Toast)
 *
 * <p>관심종목 변경처럼 화면이 즉시 반영하는 동작의 확인, 그리고 주문 접수·체결처럼
 * 사용자가 다른 영역을 보고 있어도 알아야 하는 결과에만 쓴다.
 * 오류의 <b>원인</b>은 토스트가 아니라 해당 입력란 옆에 남긴다.
 */
export type ToastTone = 'info' | 'error'

export interface Toast {
  id: number
  message: string
  tone: ToastTone
}

interface ToastState {
  toasts: Toast[]
  push: (message: string, tone?: ToastTone) => void
  dismiss: (id: number) => void
}

const VISIBLE_MS = 3200
let nextId = 1

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],

  push: (message, tone = 'info') => {
    const id = nextId++
    set((state) => ({ toasts: [...state.toasts, { id, message, tone }] }))
    window.setTimeout(() => get().dismiss(id), VISIBLE_MS)
  },

  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((toast) => toast.id !== id) })),
}))
