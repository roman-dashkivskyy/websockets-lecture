import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { apiRequest } from '../api/client'
import { readSession, writeSession } from '../util/sessionStore'
import { AuthContext, type Session } from './AuthContext'

const SESSION_KEY = 'session'

function restoreSession(): Session | null {
  const raw = readSession(SESSION_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw) as Session
    return new Date(session.expiresAt) > new Date() ? session : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(restoreSession)
  const [notice, setNotice] = useState<string | null>(null)

  const login = useCallback(async (username: string, password: string) => {
    const result = await apiRequest<Session>('/auth/login', {
      method: 'POST',
      body: { username, password },
    })
    writeSession(SESSION_KEY, JSON.stringify(result))
    setNotice(null)
    setSession(result)
  }, [])

  const logout = useCallback((reason?: string) => {
    writeSession(SESSION_KEY, null)
    setNotice(reason ?? null)
    setSession(null)
  }, [])

  const value = useMemo(() => ({ session, notice, login, logout }), [session, notice, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
