package ua.khpi.websockets.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ua.khpi.websockets.todo.TodoDtos.TodoListDto;
import ua.khpi.websockets.todo.TodoEvents.ListChanged;
import ua.khpi.websockets.todo.TodoEvents.ListDeleted;
import ua.khpi.websockets.todo.Visibility;

/**
 * Turns todo events into WebSocket messages. Runs only AFTER the transaction commits,
 * so clients never see a change that was rolled back.
 * Public lists go to everyone, private lists only to their owner's tabs.
 */
@Component
class TodoNotifier {

	/** JSON sent to the browser: {"type": "LIST_CHANGED", "list": {...}}. */
	record ListChangedMessage(String type, TodoListDto list) {
		ListChangedMessage(TodoListDto list) {
			this("LIST_CHANGED", list);
		}
	}

	/** JSON sent to the browser: {"type": "LIST_DELETED", "listId": 42}. */
	record ListDeletedMessage(String type, long listId) {
		ListDeletedMessage(long listId) {
			this("LIST_DELETED", listId);
		}
	}

	private final SessionRegistry registry;

	TodoNotifier(SessionRegistry registry) {
		this.registry = registry;
	}

	@TransactionalEventListener
	void onListChanged(ListChanged event) {
		TodoListDto list = event.list();
		send(list.visibility(), list.owner(), new ListChangedMessage(list));
	}

	@TransactionalEventListener
	void onListDeleted(ListDeleted event) {
		send(event.visibility(), event.owner(), new ListDeletedMessage(event.listId()));
	}

	private void send(Visibility visibility, String owner, Object message) {
		if (visibility == Visibility.PUBLIC) {
			registry.notifyAll(message);
		}
		else {
			registry.notifyUser(owner, message);
		}
	}

}
