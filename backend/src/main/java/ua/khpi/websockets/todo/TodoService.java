package ua.khpi.websockets.todo;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ua.khpi.websockets.auth.AppUser;
import ua.khpi.websockets.auth.AppUserRepository;
import ua.khpi.websockets.todo.TodoDtos.CreateItemRequest;
import ua.khpi.websockets.todo.TodoDtos.CreateListRequest;
import ua.khpi.websockets.todo.TodoDtos.ReplaceItemRequest;
import ua.khpi.websockets.todo.TodoDtos.ReplaceListRequest;
import ua.khpi.websockets.todo.TodoDtos.TodoItemDto;
import ua.khpi.websockets.todo.TodoDtos.TodoListDto;
import ua.khpi.websockets.todo.TodoDtos.UpdateItemRequest;
import ua.khpi.websockets.todo.TodoEvents.ListChanged;
import ua.khpi.websockets.todo.TodoEvents.ListDeleted;
import ua.khpi.websockets.todo.TodoEvents.ListsReplaced;
import ua.khpi.websockets.todo.TodoEvents.RemovedList;

/**
 * Access rules in one place:
 * <ul>
 * <li>everyone sees PUBLIC lists, only the owner sees a PRIVATE list;</li>
 * <li>items of any visible list can be added, changed and removed;</li>
 * <li>only the owner can rename or delete a list.</li>
 * </ul>
 * Somebody else's private list answers 404, so we don't reveal that it exists.
 * <p>
 * Every change publishes a {@link TodoEvents} event that names WHICH lists changed (ids only).
 */
@Service
@Transactional
public class TodoService {

	private final TodoListRepository lists;
	private final TodoItemRepository items;
	private final AppUserRepository users;
	private final ApplicationEventPublisher events;

	TodoService(TodoListRepository lists, TodoItemRepository items, AppUserRepository users,
			ApplicationEventPublisher events) {
		this.lists = lists;
		this.items = items;
		this.users = users;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public List<TodoListDto> visibleLists(String username) {
		return lists.findVisibleTo(username).stream().map(TodoListDto::from).toList();
	}

	public TodoListDto createList(String username, CreateListRequest request) {
		AppUser owner = existingUser(username);
		TodoList list = lists.save(new TodoList(request.title().strip(), request.visibility(), owner));
		return changed(list);
	}

	public TodoListDto renameList(String username, long listId, String title) {
		TodoList list = ownedList(username, listId);
		list.setTitle(title.strip());
		return changed(list);
	}

	public void deleteList(String username, long listId) {
		TodoList list = ownedList(username, listId);
		lists.delete(list);
		events.publishEvent(new ListDeleted(listId, list.getVisibility(), list.getOwner().getUsername()));
	}

	/**
	 * Replace ALL of the user's own lists with the given ones (other users' lists are untouched).
	 * The request can be huge (thousands of items), but the event stays tiny: just the ids.
	 */
	public List<TodoListDto> replaceMyLists(String username, List<ReplaceListRequest> request) {
		long ownerId = existingUser(username).getId(); // 401 for an unknown user, before touching anything
		List<RemovedList> removed = lists.findByOwnerUsername(username).stream()
			.map(list -> new RemovedList(list.getId(), list.getVisibility()))
			.toList();
		lists.deleteAllOwnedBy(ownerId);
		AppUser owner = existingUser(username); // load again: the bulk delete cleared the persistence context

		List<TodoList> created = request.stream().map(r -> {
			TodoList list = new TodoList(r.title().strip(), r.visibility(), owner);
			for (ReplaceItemRequest item : r.items()) {
				list.addItem(item.title().strip()).setDone(item.done());
			}
			return list;
		}).toList();
		lists.saveAll(created);

		events.publishEvent(new ListsReplaced(username, removed, created.stream().map(TodoList::getId).toList()));
		return created.stream().map(TodoListDto::from).toList();
	}

	public TodoItemDto addItem(String username, long listId, CreateItemRequest request) {
		TodoList list = visibleList(username, listId);
		TodoItem item = items.save(list.addItem(request.title().strip()));
		changed(list);
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
		changed(item.getList());
		return TodoItemDto.from(item);
	}

	public void deleteItem(String username, long itemId) {
		TodoItem item = visibleItem(username, itemId);
		TodoList list = item.getList();
		list.removeItem(item);
		changed(list);
	}

	/**
	 * Announce WHICH list changed (just its id); listeners act only after the transaction commits.
	 * Receivers re-read the current state, so two concurrent edits can't leave a stale list on screen.
	 */
	private TodoListDto changed(TodoList list) {
		events.publishEvent(new ListChanged(list.getId()));
		return TodoListDto.from(list);
	}

	private AppUser existingUser(String username) {
		// A valid token for a user that no longer exists (e.g. the DB was reset): ask to sign in again.
		return users.findByUsername(username)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user, please sign in again"));
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
