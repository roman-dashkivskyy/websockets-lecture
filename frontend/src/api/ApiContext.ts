import { createContext, useContext } from 'react'
import type { BackendTarget } from '../config/backends'
import type { RequestOptions } from './client'

export interface ApiContextValue {
  /** Which backend the UI talks to: the load balancer or one fixed instance. */
  target: BackendTarget
  setTarget: (id: string) => void
  /** Name of the instance that answered the last HTTP request. */
  answeredBy: string | null
  request: <T>(path: string, options?: RequestOptions) => Promise<T>
}

export const ApiContext = createContext<ApiContextValue | null>(null)

export function useApi(): ApiContextValue {
  const value = useContext(ApiContext)
  if (!value) throw new Error('useApi must be used inside ApiProvider')
  return value
}
