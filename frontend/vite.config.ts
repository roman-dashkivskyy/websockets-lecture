import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// Dev-only proxy that mimics what nginx does in Docker: /api/... (and the /api/ws WebSocket) -> the backend.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendUrl = env.BACKEND_URL ?? 'http://localhost:8081'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: { '/api': { target: backendUrl, ws: true } },
    },
  }
})
