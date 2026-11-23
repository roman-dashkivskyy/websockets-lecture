package ua.khpi.websockets.todo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.khpi.websockets.todo.TodoDtos.TodoListDto;

/** Read-only access for event processors: load the CURRENT state of lists named in an event. */
@Service
@Transactional(readOnly = true)
public class TodoQueries {

	private final TodoListRepository lists;

	TodoQueries(TodoListRepository lists) {
		this.lists = lists;
	}

	/** Empty if the list was deleted in the meantime. */
	public Optional<TodoListDto> list(long listId) {
		return lists.findWithItemsById(listId).map(TodoListDto::from);
	}

	public List<TodoListDto> lists(Collection<Long> listIds) {
		if (listIds.isEmpty()) {
			return List.of();
		}
		return lists.findWithItemsByIdInOrderById(listIds).stream().map(TodoListDto::from).toList();
	}

}
