import { useMemo } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useApi } from './ApiContext'
import { ApiError, type RequestOptions } from './client'

export type Visibility = 'PUBLIC' | 'PRIVATE'

export interface TodoItem {
  id: number
  title: string
  done: boolean
}

export interface TodoList {
  id: number
  title: string
  visibility: Visibility
  owner: string
  items: TodoItem[]
}

/** Todo endpoints, called with the current user's token. A 401 sends the user back to the login page. */
export function useTodoApi() {
  const { request } = useApi()
  const { session, logout } = useAuth()
  const token = session?.token

  return useMemo(() => {
    const call = async <T,>(path: string, options: RequestOptions = {}) => {
      try {
        return await request<T>(path, { ...options, token })
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) logout('Your session has expired. Please sign in again.')
        throw e
      }
    }

    return {
      lists: () => call<TodoList[]>('/lists'),
      createList: (title: string, visibility: Visibility) =>
        call<TodoList>('/lists', { method: 'POST', body: { title, visibility } }),
      renameList: (listId: number, title: string) =>
        call<TodoList>(`/lists/${listId}`, { method: 'PATCH', body: { title } }),
      deleteList: (listId: number) => call<void>(`/lists/${listId}`, { method: 'DELETE' }),
      addItem: (listId: number, title: string) =>
        call<TodoItem>(`/lists/${listId}/items`, { method: 'POST', body: { title } }),
      updateItem: (itemId: number, changes: Partial<Pick<TodoItem, 'title' | 'done'>>) =>
        call<TodoItem>(`/items/${itemId}`, { method: 'PATCH', body: changes }),
      deleteItem: (itemId: number) => call<void>(`/items/${itemId}`, { method: 'DELETE' }),
    }
  }, [request, token, logout])
}
