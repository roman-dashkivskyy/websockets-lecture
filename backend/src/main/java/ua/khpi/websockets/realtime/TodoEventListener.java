package ua.khpi.websockets.realtime;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Receives every todo event from this instance's queue (no matter which instance published it)
 * and pushes it to the browsers connected to THIS instance.
 */
@Component
class TodoEventListener {

	private static final Logger log = LoggerFactory.getLogger(TodoEventListener.class);

	private final SessionRegistry registry;

	TodoEventListener(SessionRegistry registry) {
		this.registry = registry;
	}

	@RabbitListener(queues = "#{todoEventsQueue.name}")
	void onEvent(Message message) {
		String json = new String(message.getBody(), StandardCharsets.UTF_8); // already the browser's JSON
		String audience = message.getMessageProperties().getHeader(TodoEventPublisher.AUDIENCE);
		String username = message.getMessageProperties().getHeader(TodoEventPublisher.USERNAME);

		// Route only on values we know. Anything else is dropped, never broadcast: a private list must not
		// reach everybody because of a missing or misspelled header.
		if (TodoEventPublisher.EVERYONE.equals(audience)) {
			registry.notifyAllJson(json);
		}
		else if (TodoEventPublisher.ONE_USER.equals(audience) && username != null) {
			registry.notifyUserJson(username, json);
		}
		else {
			log.warn("Ignoring todo event with unexpected headers: audience={}, username={}", audience, username);
		}
	}

}
