package ua.khpi.websockets.realtime;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import ua.khpi.websockets.todo.TodoDtos.TodoListDto;
import ua.khpi.websockets.todo.TodoQueries;
import ua.khpi.websockets.todo.Visibility;

/** The three spec processors. Each re-reads the CURRENT data, so a slow or reordered message can't show stale lists. */
final class SpecProcessors {

	private SpecProcessors() {
	}

	/** {"listId": 42} → load list 42 and push it. Ignored if the list was deleted in the meantime. */
	@Component
	static class ListChangedProcessor implements SpecProcessor {

		private final TodoQueries queries;
		private final SocketRouter router;

		ListChangedProcessor(TodoQueries queries, SocketRouter router) {
			this.queries = queries;
			this.router = router;
		}

		@Override
		public String name() {
			return LIST_CHANGED;
		}

		@Override
		public void process(JsonNode params) {
			queries.list(params.required("listId").asLong())
				.ifPresent(list -> router.send(list.visibility(), list.owner(), new SocketMessages.ListChanged(list)));
		}

	}

	/** {"listId": 42, "visibility": "PRIVATE", "owner": "alice"}: a deleted list can't be re-read, so the spec says who. */
	@Component
	static class ListDeletedProcessor implements SpecProcessor {

		private final SocketRouter router;

		ListDeletedProcessor(SocketRouter router) {
			this.router = router;
		}

		@Override
		public String name() {
			return LIST_DELETED;
		}

		@Override
		public void process(JsonNode params) {
			router.send(Visibility.valueOf(params.required("visibility").asString()), params.required("owner").asString(),
					new SocketMessages.ListDeleted(params.required("listId").asLong()));
		}

	}

	/**
	 * {"owner": "alice", "removed": [{"listId": 1, "visibility": "PUBLIC"}, ...], "createdIds": [7, 8]}
	 * → tell who the old lists are gone, then load all new lists in one query and push them.
	 */
	@Component
	static class ListsReplacedProcessor implements SpecProcessor {

		private final TodoQueries queries;
		private final SocketRouter router;

		ListsReplacedProcessor(TodoQueries queries, SocketRouter router) {
			this.queries = queries;
			this.router = router;
		}

		@Override
		public String name() {
			return LISTS_REPLACED;
		}

		@Override
		public void process(JsonNode params) {
			String owner = params.required("owner").asString();
			for (JsonNode removed : params.required("removed")) {
				router.send(Visibility.valueOf(removed.required("visibility").asString()), owner,
						new SocketMessages.ListDeleted(removed.required("listId").asLong()));
			}
			List<Long> createdIds = new ArrayList<>();
			params.required("createdIds").forEach(id -> createdIds.add(id.asLong()));
			for (TodoListDto list : queries.lists(createdIds)) {
				router.send(list.visibility(), list.owner(), new SocketMessages.ListChanged(list));
			}
		}

	}

}
