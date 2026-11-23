import { createContext, useContext } from 'react'

export interface Session {
  token: string
  username: string
  displayName: string
  expiresAt: string
}

export interface AuthContextValue {
  session: Session | null
  /** Shown on the login page, e.g. after the session expired. */
  notice: string | null
  login: (username: string, password: string) => Promise<void>
  logout: (notice?: string) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
