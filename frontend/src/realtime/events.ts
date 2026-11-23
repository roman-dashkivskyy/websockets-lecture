import type { TodoList } from '../api/todoApi'

/** Changes pushed by the server over the WebSocket (see TodoNotifier on the backend). */
export type TodoEvent = { type: 'LIST_CHANGED'; list: TodoList } | { type: 'LIST_DELETED'; listId: number }

/** First message on every socket: which backend instance holds this connection. */
export type ConnectedMessage = { type: 'CONNECTED'; instance: string }

export type ServerMessage = TodoEvent | ConnectedMessage

/** Apply one pushed change to the lists on screen. A LIST_CHANGED carries the whole new list. */
export function applyEvent(lists: TodoList[], event: TodoEvent): TodoList[] {
  switch (event.type) {
    case 'LIST_CHANGED': {
      const exists = lists.some((l) => l.id === event.list.id)
      return exists ? lists.map((l) => (l.id === event.list.id ? event.list : l)) : [...lists, event.list]
    }
    case 'LIST_DELETED':
      return lists.filter((l) => l.id !== event.listId)
  }
}
