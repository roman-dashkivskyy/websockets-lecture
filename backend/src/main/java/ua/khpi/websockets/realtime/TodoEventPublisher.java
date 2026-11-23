package ua.khpi.websockets.realtime;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;
import ua.khpi.websockets.todo.TodoDtos.TodoListDto;
import ua.khpi.websockets.todo.TodoEvents.ListChanged;
import ua.khpi.websockets.todo.TodoEvents.ListDeleted;
import ua.khpi.websockets.todo.Visibility;

/**
 * After a change is committed, publish it to RabbitMQ instead of pushing to our own sockets.
 * Every instance (this one included) gets it back from its own queue: see {@link TodoEventListener}.
 * <p>
 * The body is exactly the JSON the browser will receive. The headers say who should receive it.
 */
@Component
class TodoEventPublisher {

	static final String AUDIENCE = "audience";
	static final String USERNAME = "username";
	static final String EVERYONE = "all";
	static final String ONE_USER = "user";

	private static final Logger log = LoggerFactory.getLogger(TodoEventPublisher.class);

	private final RabbitTemplate rabbit;
	private final JsonMapper jsonMapper;

	TodoEventPublisher(RabbitTemplate rabbit, JsonMapper jsonMapper) {
		this.rabbit = rabbit;
		this.jsonMapper = jsonMapper;
	}

	@TransactionalEventListener
	void onListChanged(ListChanged event) {
		TodoListDto list = event.list();
		publish(list.visibility(), list.owner(), new SocketMessages.ListChanged(list));
	}

	@TransactionalEventListener
	void onListDeleted(ListDeleted event) {
		publish(event.visibility(), event.owner(), new SocketMessages.ListDeleted(event.listId()));
	}

	private void publish(Visibility visibility, String owner, Object socketMessage) {
		Message message = MessageBuilder
			.withBody(jsonMapper.writeValueAsString(socketMessage).getBytes(StandardCharsets.UTF_8))
			.setContentType(MessageProperties.CONTENT_TYPE_JSON)
			.setContentEncoding(StandardCharsets.UTF_8.name())
			.setHeader(AUDIENCE, visibility == Visibility.PUBLIC ? EVERYONE : ONE_USER)
			.setHeader(USERNAME, owner)
			.build();
		try {
			rabbit.send(BrokerConfig.EXCHANGE, "", message); // a fanout exchange ignores the routing key
		}
		catch (AmqpException ex) {
			// The change is already committed, so don't fail the HTTP request. The event is lost, though:
			// the production fix is the "transactional outbox" pattern (store events in the DB, publish later).
			log.error("Could not publish todo event, browsers won't be notified: {}", ex.toString());
		}
	}

}
