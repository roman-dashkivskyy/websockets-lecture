import { useEffect, useRef, useState } from 'react'
import type { TodoEvent } from './events'

export type SocketStatus = 'connecting' | 'live' | 'closed'

/**
 * Opens ws(s)://<this host>/api/ws?token=... and calls onEvent for every pushed message.
 * The browser's WebSocket API can't set an Authorization header, so the token goes in the URL.
 * No automatic reconnect yet: that comes with several backend instances (phase 3).
 */
export function useTodoSocket(token: string, onEvent: (event: TodoEvent) => void): SocketStatus {
  const [status, setStatus] = useState<SocketStatus>('connecting')

  // always call the latest callback without reconnecting when it changes
  const onEventRef = useRef(onEvent)
  useEffect(() => {
    onEventRef.current = onEvent
  })

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const socket = new WebSocket(`${protocol}://${window.location.host}/api/ws?token=${encodeURIComponent(token)}`)

    socket.onopen = () => setStatus('live')
    socket.onmessage = (message: MessageEvent<string>) => onEventRef.current(JSON.parse(message.data) as TodoEvent)
    socket.onclose = () => setStatus('closed')

    return () => {
      // closing on purpose (sign out, unmount): ignore whatever this socket still reports
      socket.onopen = null
      socket.onmessage = null
      socket.onclose = null
      socket.close()
    }
  }, [token])

  return status
}
