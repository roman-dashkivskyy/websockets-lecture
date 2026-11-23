package ua.khpi.websockets.todo;

import java.util.List;

/**
 * Published by {@link TodoService} after every change. They carry only identifiers: whoever needs the data
 * re-reads it (see TodoQueries). The todo package doesn't know who listens.
 */
public final class TodoEvents {

	private TodoEvents() {
	}

	/** A list was created or changed (title, items). */
	public record ListChanged(long listId) {
	}

	/** A list was deleted. It can't be re-read anymore, so the event keeps what's needed to tell the right users. */
	public record ListDeleted(long listId, Visibility visibility, String owner) {
	}

	/** All lists of one user were replaced at once (PUT /api/lists/mine). */
	public record ListsReplaced(String owner, List<RemovedList> removed, List<Long> createdIds) {
	}

	public record RemovedList(long listId, Visibility visibility) {
	}

}
