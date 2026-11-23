package ua.khpi.websockets.realtime;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;
import ua.khpi.websockets.todo.TodoEvents.ListChanged;
import ua.khpi.websockets.todo.TodoEvents.ListsReplaced;
import ua.khpi.websockets.todo.TodoEvents.RemovedList;
import ua.khpi.websockets.todo.Visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TodoEventPublisherTest {

	private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
	private final TodoEventPublisher publisher = new TodoEventPublisher(rabbit, JsonMapper.builder().build());

	@Test
	void changeIsPublishedAsATinySpecWithTheProcessorInTheHeader() {
		publisher.onListChanged(new ListChanged(42));

		Message message = sentMessage();
		assertThat((String) message.getMessageProperties().getHeader(SpecProcessor.HEADER)).isEqualTo("LIST_CHANGED");
		assertThat(body(message)).isEqualTo("{\"listId\":42}");
	}

	@Test
	void replacingFiftyListsWithThousandsOfItemsIsStillASmallMessage() {
		// 50 lists x 200 items = 10,000 items in the HTTP request; the broker only gets the ids
		List<RemovedList> removed = LongStream.rangeClosed(1, 50)
			.mapToObj(id -> new RemovedList(id, Visibility.PUBLIC))
			.toList();
		List<Long> created = LongStream.rangeClosed(1001, 1050).boxed().toList();

		publisher.onListsReplaced(new ListsReplaced("alice", removed, created));

		Message message = sentMessage();
		assertThat((String) message.getMessageProperties().getHeader(SpecProcessor.HEADER)).isEqualTo("LISTS_REPLACED");
		assertThat(message.getBody().length).as("spec size in bytes").isLessThan(3 * 1024);
		assertThat(body(message)).doesNotContain("items");
	}

	@Test
	void brokerDownIsLoggedNotThrown() {
		// The change is already committed; the HTTP request must still succeed (the event is lost).
		doThrow(new AmqpConnectException(new ConnectException("broker down")))
			.when(rabbit).send(anyString(), anyString(), any(Message.class));

		assertThatCode(() -> publisher.onListChanged(new ListChanged(5))).doesNotThrowAnyException();
	}

	private Message sentMessage() {
		ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
		verify(rabbit).send(eq(BrokerConfig.EXCHANGE), eq(""), sent.capture());
		return sent.getValue();
	}

	private static String body(Message message) {
		return new String(message.getBody(), StandardCharsets.UTF_8);
	}

}
