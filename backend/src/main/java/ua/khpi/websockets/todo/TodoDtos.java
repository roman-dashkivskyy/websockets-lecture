package ua.khpi.websockets.todo;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the todo API. */
public final class TodoDtos {

	private TodoDtos() {
	}

	public record TodoItemDto(Long id, String title, boolean done) {

		static TodoItemDto from(TodoItem item) {
			return new TodoItemDto(item.getId(), item.getTitle(), item.isDone());
		}

	}

	/**
	 * The same snapshot goes to every client (HTTP response or WebSocket push), so it has no per-user fields:
	 * the UI compares {@code owner} with the signed-in user itself.
	 */
	public record TodoListDto(Long id, String title, Visibility visibility, String owner, List<TodoItemDto> items) {

		static TodoListDto from(TodoList list) {
			return new TodoListDto(list.getId(), list.getTitle(), list.getVisibility(), list.getOwner().getUsername(),
					list.getItems().stream().map(TodoItemDto::from).toList());
		}

	}

	public record CreateListRequest(@NotBlank @Size(max = 100) String title, @NotNull Visibility visibility) {
	}

	public record RenameListRequest(@NotBlank @Size(max = 100) String title) {
	}

	public record CreateItemRequest(@NotBlank @Size(max = 200) String title) {
	}

	/** Both fields are optional: send only what changes. A title, if sent, must not be blank. */
	public record UpdateItemRequest(@Size(max = 200) @Pattern(regexp = "(?s).*\\S.*") String title, Boolean done) {
	}

}
