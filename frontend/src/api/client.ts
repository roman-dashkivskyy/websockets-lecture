/** Header set by every backend instance (see InstanceHeaderFilter). */
export const INSTANCE_HEADER = 'X-Instance-Name'

/** Error with the HTTP status and the "detail" of a Spring ProblemDetail body. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  token?: string | null
}

/**
 * fetch() wrapper: JSON in/out, bearer token, ProblemDetail errors.
 * basePath is "/api" (load balancer) or "/i/N/api" (one instance).
 * Calls onInstance with the name of the backend instance that answered.
 */
export async function apiRequest<T>(
  basePath: string,
  path: string,
  { method = 'GET', body, token }: RequestOptions,
  onInstance: (name: string) => void,
): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${basePath}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: 'no-store',
  })

  const instance = response.headers.get(INSTANCE_HEADER)
  if (instance) onInstance(instance)

  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response))
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: string; title?: string }
    return problem.detail ?? problem.title ?? response.statusText
  } catch {
    return response.status === 401 ? 'Please sign in' : `Request failed (${response.status})`
  }
}
