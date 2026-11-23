package ua.khpi.websockets.todo;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ua.khpi.websockets.auth.AppUser;
import ua.khpi.websockets.auth.AppUserRepository;
import ua.khpi.websockets.todo.TodoDtos.CreateItemRequest;
import ua.khpi.websockets.todo.TodoDtos.CreateListRequest;
import ua.khpi.websockets.todo.TodoDtos.TodoItemDto;
import ua.khpi.websockets.todo.TodoDtos.TodoListDto;
import ua.khpi.websockets.todo.TodoDtos.UpdateItemRequest;

/**
 * Access rules in one place:
 * <ul>
 * <li>everyone sees PUBLIC lists, only the owner sees a PRIVATE list;</li>
 * <li>items of any visible list can be added, changed and removed;</li>
 * <li>only the owner can rename or delete a list.</li>
 * </ul>
 * Somebody else's private list answers 404, so we don't reveal that it exists.
 */
@Service
@Transactional
public class TodoService {

	private final TodoListRepository lists;
	private final TodoItemRepository items;
	private final AppUserRepository users;

	TodoService(TodoListRepository lists, TodoItemRepository items, AppUserRepository users) {
		this.lists = lists;
		this.items = items;
		this.users = users;
	}

	@Transactional(readOnly = true)
	public List<TodoListDto> visibleLists(String username) {
		return lists.findVisibleTo(username).stream().map(list -> TodoListDto.from(list, username)).toList();
	}

	public TodoListDto createList(String username, CreateListRequest request) {
		// A valid token for a user that no longer exists (e.g. the DB was reset): ask to sign in again.
		AppUser owner = users.findByUsername(username)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user, please sign in again"));
		TodoList list = lists.save(new TodoList(request.title().strip(), request.visibility(), owner));
		return TodoListDto.from(list, username);
	}

	public TodoListDto renameList(String username, long listId, String title) {
		TodoList list = ownedList(username, listId);
		list.setTitle(title.strip());
		return TodoListDto.from(list, username);
	}

	public void deleteList(String username, long listId) {
		lists.delete(ownedList(username, listId));
	}

	public TodoItemDto addItem(String username, long listId, CreateItemRequest request) {
		TodoItem item = items.save(new TodoItem(visibleList(username, listId), request.title().strip()));
		return TodoItemDto.from(item);
	}

	public TodoItemDto updateItem(String username, long itemId, UpdateItemRequest request) {
		TodoItem item = visibleItem(username, itemId);
		if (request.title() != null) {
			item.setTitle(request.title().strip());
		}
		if (request.done() != null) {
			item.setDone(request.done());
		}
		return TodoItemDto.from(item);
	}

	public void deleteItem(String username, long itemId) {
		TodoItem item = visibleItem(username, itemId);
		item.getList().removeItem(item);
	}

	private TodoList visibleList(String username, long listId) {
		return lists.findById(listId)
			.filter(list -> list.isVisibleTo(username))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "List not found"));
	}

	private TodoList ownedList(String username, long listId) {
		TodoList list = visibleList(username, listId);
		if (!list.isOwnedBy(username)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can change this list");
		}
		return list;
	}

	private TodoItem visibleItem(String username, long itemId) {
		return items.findById(itemId)
			.filter(item -> item.getList().isVisibleTo(username))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
	}

}
