package ua.khpi.websockets.todo;

import ua.khpi.websockets.todo.TodoDtos.TodoListDto;

/**
 * Published by {@link TodoService} after every change. The todo package doesn't know who listens:
 * in phase 2 it's the WebSocket notifier.
 */
public final class TodoEvents {

	private TodoEvents() {
	}

	/** A list was created or changed (title, items). Carries the full new state of the list. */
	public record ListChanged(TodoListDto list) {
	}

	/** A list was deleted. Visibility and owner decide who must be told. */
	public record ListDeleted(long listId, Visibility visibility, String owner) {
	}

}
