import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { backends } from '../config/backends'
import { readSession, writeSession } from '../util/sessionStore'
import { ApiContext } from './ApiContext'
import { apiRequest, type RequestOptions } from './client'

const TARGET_KEY = 'target'

export function ApiProvider({ children }: { children: ReactNode }) {
  const [targetId, setTargetId] = useState(() => readSession(TARGET_KEY) ?? backends[0].id)
  const [answeredBy, setAnsweredBy] = useState<string | null>(null)

  const target = backends.find((b) => b.id === targetId) ?? backends[0]

  const setTarget = useCallback((id: string) => {
    setTargetId(id)
    writeSession(TARGET_KEY, id)
  }, [])

  const request = useCallback(
    <T,>(path: string, options: RequestOptions = {}) =>
      apiRequest<T>(target.basePath, path, options, setAnsweredBy),
    [target.basePath],
  )

  const value = useMemo(
    () => ({ target, setTarget, answeredBy, request }),
    [target, setTarget, answeredBy, request],
  )
  return <ApiContext.Provider value={value}>{children}</ApiContext.Provider>
}
