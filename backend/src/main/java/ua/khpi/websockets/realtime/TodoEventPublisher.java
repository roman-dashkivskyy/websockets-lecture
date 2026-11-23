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
import ua.khpi.websockets.todo.TodoEvents.ListChanged;
import ua.khpi.websockets.todo.TodoEvents.ListDeleted;
import ua.khpi.websockets.todo.TodoEvents.ListsReplaced;

/**
 * After a change is committed, publish a small "spec" to RabbitMQ: WHICH processor should run and WITH WHICH ids.
 * Never the lists themselves: a replace of thousands of items is still a message of a few dozen bytes, and the
 * broker copies it into every instance's queue. Each instance's processor re-reads the data from the database
 * (see {@link SpecProcessor} and {@link TodoEventListener}).
 */
@Component
class TodoEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(TodoEventPublisher.class);

	private final RabbitTemplate rabbit;
	private final JsonMapper jsonMapper;

	TodoEventPublisher(RabbitTemplate rabbit, JsonMapper jsonMapper) {
		this.rabbit = rabbit;
		this.jsonMapper = jsonMapper;
	}

	@TransactionalEventListener
	void onListChanged(ListChanged event) {
		publish(SpecProcessor.LIST_CHANGED, event); // {"listId": 42}
	}

	@TransactionalEventListener
	void onListDeleted(ListDeleted event) {
		publish(SpecProcessor.LIST_DELETED, event); // {"listId": 42, "visibility": "PRIVATE", "owner": "alice"}
	}

	@TransactionalEventListener
	void onListsReplaced(ListsReplaced event) {
		publish(SpecProcessor.LISTS_REPLACED, event); // {"owner": "alice", "removed": [...], "createdIds": [...]}
	}

	private void publish(String processor, Object params) {
		byte[] body = jsonMapper.writeValueAsBytes(params);
		Message message = MessageBuilder.withBody(body)
			.setContentType(MessageProperties.CONTENT_TYPE_JSON)
			.setContentEncoding(StandardCharsets.UTF_8.name())
			.setHeader(SpecProcessor.HEADER, processor)
			.build();
		try {
			rabbit.send(BrokerConfig.EXCHANGE, "", message); // a fanout exchange ignores the routing key
			log.debug("Published {} spec ({} bytes)", processor, body.length);
		}
		catch (AmqpException ex) {
			// The change is already committed, so don't fail the HTTP request. The event is lost, though:
			// the production fix is the "transactional outbox" pattern (store events in the DB, publish later).
			log.error("Could not publish {} spec, browsers won't be notified: {}", processor, ex.toString());
		}
	}

}
