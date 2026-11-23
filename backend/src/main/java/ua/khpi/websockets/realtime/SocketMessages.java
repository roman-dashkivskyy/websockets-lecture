package ua.khpi.websockets.realtime;

import ua.khpi.websockets.todo.TodoDtos.TodoListDto;

/** The JSON messages a browser receives over the WebSocket. */
final class SocketMessages {

	private SocketMessages() {
	}

	/** {"type": "LIST_CHANGED", "list": {...}}: the whole new state of one list. */
	record ListChanged(String type, TodoListDto list) {
		ListChanged(TodoListDto list) {
			this("LIST_CHANGED", list);
		}
	}

	/** {"type": "LIST_DELETED", "listId": 42}. */
	record ListDeleted(String type, long listId) {
		ListDeleted(long listId) {
			this("LIST_DELETED", listId);
		}
	}

}
