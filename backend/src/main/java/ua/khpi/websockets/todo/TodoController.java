package ua.khpi.websockets.todo;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.khpi.websockets.todo.TodoDtos.CreateItemRequest;
import ua.khpi.websockets.todo.TodoDtos.CreateListRequest;
import ua.khpi.websockets.todo.TodoDtos.RenameListRequest;
import ua.khpi.websockets.todo.TodoDtos.TodoItemDto;
import ua.khpi.websockets.todo.TodoDtos.TodoListDto;
import ua.khpi.websockets.todo.TodoDtos.UpdateItemRequest;

/** Plain HTTP: every change is visible to other users only after they reload the lists. */
@RestController
@RequestMapping("/api")
public class TodoController {

	private final TodoService todos;

	TodoController(TodoService todos) {
		this.todos = todos;
	}

	@GetMapping("/lists")
	public List<TodoListDto> lists(Authentication auth) {
		return todos.visibleLists(auth.getName());
	}

	@PostMapping("/lists")
	@ResponseStatus(HttpStatus.CREATED)
	public TodoListDto createList(Authentication auth, @Valid @RequestBody CreateListRequest request) {
		return todos.createList(auth.getName(), request);
	}

	@PatchMapping("/lists/{listId}")
	public TodoListDto renameList(Authentication auth, @PathVariable long listId,
			@Valid @RequestBody RenameListRequest request) {
		return todos.renameList(auth.getName(), listId, request.title());
	}

	@DeleteMapping("/lists/{listId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteList(Authentication auth, @PathVariable long listId) {
		todos.deleteList(auth.getName(), listId);
	}

	@PostMapping("/lists/{listId}/items")
	@ResponseStatus(HttpStatus.CREATED)
	public TodoItemDto addItem(Authentication auth, @PathVariable long listId,
			@Valid @RequestBody CreateItemRequest request) {
		return todos.addItem(auth.getName(), listId, request);
	}

	@PatchMapping("/items/{itemId}")
	public TodoItemDto updateItem(Authentication auth, @PathVariable long itemId,
			@Valid @RequestBody UpdateItemRequest request) {
		return todos.updateItem(auth.getName(), itemId, request);
	}

	@DeleteMapping("/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteItem(Authentication auth, @PathVariable long itemId) {
		todos.deleteItem(auth.getName(), itemId);
	}

}
