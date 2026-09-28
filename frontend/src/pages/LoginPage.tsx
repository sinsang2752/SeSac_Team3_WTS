import { Navigate, useLocation, useNavigate } from 'react-router'

import { LoginPanel } from '../features/auth/LoginPanel'
import { useUserStore } from '../stores/userStore'

/** `/login` (CLAUDE.md §26, ui-requirements §8) */
export function LoginPage() {
  const session = useUserStore((state) => state.session)
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/wts'

  if (session) {
    return <Navigate to={from} replace />
  }

  return (
    <div className="page page--narrow">
      <LoginPanel onSuccess={() => navigate(from, { replace: true })} />
    </div>
  )
}
