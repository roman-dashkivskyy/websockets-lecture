export interface BackendTarget {
  id: string
  label: string
  /** Base path the UI prepends to every API call (and to the WebSocket URL). */
  basePath: string
}

const count = Number(import.meta.env.VITE_BACKEND_COUNT ?? 3)

/**
 * "Load balancer" goes through nginx's round-robin upstream,
 * "Instance N" talks to one specific backend copy (/i/N/api/...).
 */
export const backends: BackendTarget[] = [
  { id: 'lb', label: 'Load balancer', basePath: '/api' },
  ...Array.from({ length: count }, (_, i) => ({
    id: `i${i + 1}`,
    label: `Instance ${i + 1}`,
    basePath: `/i/${i + 1}/api`,
  })),
]
