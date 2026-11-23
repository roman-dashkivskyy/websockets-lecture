import { useEffect, useRef, useState } from 'react'
import type { ServerMessage, TodoEvent } from './events'

export type SocketStatus =
  | { state: 'connecting' }
  /** connection counts successful (re)connects; instance comes from the CONNECTED greeting */
  | { state: 'live'; connection: number; instance: string | null }
  /** attempt counts failed tries since the last good connection */
  | { state: 'reconnecting'; attempt: number; inSeconds: number }

const MAX_DELAY_MS = 15_000

/**
 * Opens ws(s)://<this host><basePath>/ws?token=... and calls onEvent for every pushed change.
 * The browser's WebSocket API can't set an Authorization header, so the token goes in the URL.
 * If the connection drops (e.g. its backend instance stopped), it reconnects with exponential
 * backoff: about 1 s, 2 s, 4 s ... up to 15 s, with some jitter so all tabs don't retry at once.
 */
export function useTodoSocket(basePath: string, token: string, onEvent: (event: TodoEvent) => void): SocketStatus {
  const [status, setStatus] = useState<SocketStatus>({ state: 'connecting' })

  // always call the latest callback without reconnecting when it changes
  const onEventRef = useRef(onEvent)
  useEffect(() => {
    onEventRef.current = onEvent
  })

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${window.location.host}${basePath}/ws?token=${encodeURIComponent(token)}`

    let socket: WebSocket | null = null
    let retryTimer: number | undefined
    let failedAttempts = 0
    let connection = 0
    let stopped = false

    const open = () => {
      socket = new WebSocket(url)

      socket.onopen = () => {
        connection += 1
        setStatus({ state: 'live', connection, instance: null })
      }

      socket.onmessage = (message: MessageEvent<string>) => {
        const data = JSON.parse(message.data) as ServerMessage
        if (data.type === 'CONNECTED') {
          failedAttempts = 0 // the server really greeted us: only now reset the backoff
          setStatus((current) => (current.state === 'live' ? { ...current, instance: data.instance } : current))
        } else {
          onEventRef.current(data)
        }
      }

      socket.onclose = () => {
        if (stopped) return
        const delay = Math.min(MAX_DELAY_MS, 1000 * 2 ** failedAttempts) * (0.8 + Math.random() * 0.4)
        failedAttempts += 1
        setStatus({ state: 'reconnecting', attempt: failedAttempts, inSeconds: Math.max(1, Math.round(delay / 1000)) })
        retryTimer = window.setTimeout(open, delay)
      }
    }

    open()

    return () => {
      // closing on purpose (sign out, other backend, unmount): no reconnect, ignore late events
      stopped = true
      window.clearTimeout(retryTimer)
      if (socket) {
        socket.onopen = null
        socket.onmessage = null
        socket.onclose = null
        socket.close()
      }
    }
  }, [basePath, token])

  return status
}
