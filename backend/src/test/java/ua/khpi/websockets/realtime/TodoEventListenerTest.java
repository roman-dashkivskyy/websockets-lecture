package ua.khpi.websockets.realtime;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TodoEventListenerTest {

	private static final String JSON = "{\"type\":\"LIST_DELETED\",\"listId\":1}";

	private final SessionRegistry registry = mock(SessionRegistry.class);
	private final TodoEventListener listener = new TodoEventListener(registry);

	@Test
	void everyoneAudienceGoesToAll() {
		listener.onEvent(message("all", "alice"));
		verify(registry).notifyAllJson(JSON);
		verify(registry, never()).notifyUserJson(anyString(), anyString());
	}

	@Test
	void userAudienceGoesOnlyToThatUser() {
		listener.onEvent(message("user", "alice"));
		verify(registry).notifyUserJson("alice", JSON);
		verify(registry, never()).notifyAllJson(any());
	}

	@Test
	void unexpectedHeadersAreDroppedNeverBroadcast() {
		listener.onEvent(message(null, "alice"));      // missing audience
		listener.onEvent(message("everyone", "alice")); // unknown audience
		listener.onEvent(message("user", null));        // private event without a user

		verify(registry, never()).notifyAllJson(any());
		verify(registry, never()).notifyUserJson(any(), any());
	}

	private static Message message(String audience, String username) {
		MessageBuilder builder = MessageBuilder.withBody(JSON.getBytes(StandardCharsets.UTF_8));
		if (audience != null) {
			builder.setHeader(TodoEventPublisher.AUDIENCE, audience);
		}
		if (username != null) {
			builder.setHeader(TodoEventPublisher.USERNAME, username);
		}
		return builder.build();
	}

}
