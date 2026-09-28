import { resolve } from 'node:path'

import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// 환경변수는 저장소 루트의 .env 하나로 관리한다 (CLAUDE.md §33).
const ENV_DIR = resolve(import.meta.dirname, '..')

// 개발 서버 프록시.
// 브라우저는 항상 같은 오리진으로 호출하고, 실제 라우팅은 Gateway가 담당한다.
// (CLAUDE.md §6.1 – Gateway가 클라이언트의 단일 진입점)
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ENV_DIR, '')
  const gateway = env.VITE_GATEWAY_URL ?? 'http://localhost:8080'

  return {
    plugins: [react()],
    envDir: ENV_DIR,
    server: {
      port: Number(env.FRONTEND_PORT ?? 5173),
      proxy: {
        '/api': { target: gateway, changeOrigin: true },
        // 실시간 시세 WebSocket (CLAUDE.md §25)
        '/ws': { target: gateway, changeOrigin: true, ws: true },
      },
    },
  }
})
