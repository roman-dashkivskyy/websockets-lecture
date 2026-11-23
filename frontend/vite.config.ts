import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type ProxyOptions } from 'vite'

// Dev-only proxy that mimics what nginx does in Docker (WebSocket included):
//   /api/...       -> the first backend (no real load balancer in dev)
//   /i/N/api/...   -> backend N, running on port BACKEND_BASE_PORT + N - 1
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const count = Number(env.VITE_BACKEND_COUNT ?? 3)
  const basePort = Number(env.BACKEND_BASE_PORT ?? 8081)

  const proxy: Record<string, ProxyOptions> = {
    '/api': { target: `http://localhost:${basePort}`, ws: true },
  }
  for (let i = 1; i <= count; i++) {
    proxy[`/i/${i}/api`] = {
      target: `http://localhost:${basePort + i - 1}`,
      ws: true,
      rewrite: (path) => path.replace(`/i/${i}`, ''),
    }
  }

  return {
    plugins: [react()],
    server: { port: 5173, proxy },
  }
})
