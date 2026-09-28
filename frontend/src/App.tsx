import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'

import { AppLayout } from './app/AppLayout'
import { RequireSession } from './app/RequireSession'
import { LoginPage } from './pages/LoginPage'
import { OrdersPage } from './pages/OrdersPage'
import { PortfolioPage } from './pages/PortfolioPage'
import { WtsPage } from './pages/WtsPage'
import { ApiRequestError } from './lib/api'
import { useUserStore } from './stores/userStore'


/**
 * 토큰이 만료되거나 무효해지면 세션을 버린다.
 *
 * <p>버리지 않으면 모든 조회가 401로 돌아오는 화면에 갇힌다.
 * 한곳에서 처리해야 조회·주문 어느 쪽에서 401이 나든 같게 동작한다.
 */
function discardSessionOnUnauthorized(error: Error) {
  if (error instanceof ApiRequestError && error.code === 'UNAUTHORIZED') {
    useUserStore.getState().logout()
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: discardSessionOnUnauthorized }),
  mutationCache: new MutationCache({ onError: discardSessionOnUnauthorized }),
  defaultOptions: {
    queries: {
      // 실시간 값은 WebSocket이 밀어준다. 창 포커스만으로 다시 조회할 이유가 없다.
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
})

/** 라우트는 CLAUDE.md §26을 따른다. */
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<Navigate to="/wts" replace />} />
            <Route path="/login" element={<LoginPage />} />
            {/* 시세는 공개다 (ADR-0007). 로그인 없이도 열린다. */}
            <Route path="/wts" element={<WtsPage />} />
            <Route path="/wts/:symbol" element={<WtsPage />} />
            <Route
              path="/orders"
              element={
                <RequireSession>
                  <OrdersPage />
                </RequireSession>
              }
            />
            <Route
              path="/portfolio"
              element={
                <RequireSession>
                  <PortfolioPage />
                </RequireSession>
              }
            />
            <Route path="*" element={<Navigate to="/wts" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
