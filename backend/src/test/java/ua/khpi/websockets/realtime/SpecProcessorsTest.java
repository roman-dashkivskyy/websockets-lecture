package ua.khpi.websockets.realtime;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import ua.khpi.websockets.todo.TodoDtos.TodoItemDto;
import ua.khpi.websockets.todo.TodoDtos.TodoListDto;
import ua.khpi.websockets.todo.TodoQueries;
import ua.khpi.websockets.todo.Visibility;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpecProcessorsTest {

	private final JsonMapper json = JsonMapper.builder().build();
	private final TodoQueries queries = mock(TodoQueries.class);
	private final SocketRouter router = mock(SocketRouter.class);

	private static final TodoListDto PUBLIC_LIST = new TodoListDto(42L, "Team", Visibility.PUBLIC, "alice",
			List.of(new TodoItemDto(1L, "Plan", false)));
	private static final TodoListDto PRIVATE_LIST = new TodoListDto(43L, "Diary", Visibility.PRIVATE, "alice", List.of());

	@Test
	void listChangedReReadsTheListAndRoutesItByItsVisibility() {
		when(queries.list(42)).thenReturn(Optional.of(PUBLIC_LIST));

		new SpecProcessors.ListChangedProcessor(queries, router).process(json.readTree("{\"listId\":42}"));

		verify(router).send(Visibility.PUBLIC, "alice", new SocketMessages.ListChanged(PUBLIC_LIST));
	}

	@Test
	void listThatNoLongerExistsIsIgnored() {
		when(queries.list(42)).thenReturn(Optional.empty());

		new SpecProcessors.ListChangedProcessor(queries, router).process(json.readTree("{\"listId\":42}"));

		verify(router, never()).send(any(), any(), any());
	}

	@Test
	void listDeletedIsRoutedFromTheSpecItself() {
		new SpecProcessors.ListDeletedProcessor(router)
			.process(json.readTree("{\"listId\":7,\"visibility\":\"PRIVATE\",\"owner\":\"alice\"}"));

		verify(router).send(Visibility.PRIVATE, "alice", new SocketMessages.ListDeleted(7));
	}

	@Test
	void listsReplacedRemovesOldListsAndPushesTheNewOnes() {
		when(queries.lists(List.of(42L, 43L))).thenReturn(List.of(PUBLIC_LIST, PRIVATE_LIST));

		new SpecProcessors.ListsReplacedProcessor(queries, router).process(json.readTree("""
				{"owner":"alice","removed":[{"listId":1,"visibility":"PUBLIC"},{"listId":2,"visibility":"PRIVATE"}],
				 "createdIds":[42,43]}"""));

		verify(router).send(Visibility.PUBLIC, "alice", new SocketMessages.ListDeleted(1));
		verify(router).send(Visibility.PRIVATE, "alice", new SocketMessages.ListDeleted(2));
		verify(router).send(eq(Visibility.PUBLIC), eq("alice"), eq(new SocketMessages.ListChanged(PUBLIC_LIST)));
		verify(router).send(eq(Visibility.PRIVATE), eq("alice"), eq(new SocketMessages.ListChanged(PRIVATE_LIST)));
	}

}
