package ua.khpi.websockets.realtime;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;
import ua.khpi.websockets.todo.TodoDtos.TodoListDto;
import ua.khpi.websockets.todo.TodoEvents.ListChanged;
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
	void privateListIsPublishedForItsOwnerOnly() {
		publisher.onListChanged(new ListChanged(new TodoListDto(5L, "Diary", Visibility.PRIVATE, "alice", List.of())));

		ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
		verify(rabbit).send(eq(BrokerConfig.EXCHANGE), eq(""), sent.capture());
		Message message = sent.getValue();
		assertThat((String) message.getMessageProperties().getHeader(TodoEventPublisher.AUDIENCE)).isEqualTo("user");
		assertThat((String) message.getMessageProperties().getHeader(TodoEventPublisher.USERNAME)).isEqualTo("alice");
		assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).contains("\"type\":\"LIST_CHANGED\"");
	}

	@Test
	void brokerDownIsLoggedNotThrown() {
		// The change is already committed; the HTTP request must still succeed (the event is lost).
		doThrow(new AmqpConnectException(new ConnectException("broker down")))
			.when(rabbit).send(anyString(), anyString(), any(Message.class));

		assertThatCode(() -> publisher.onListChanged(
				new ListChanged(new TodoListDto(5L, "Team", Visibility.PUBLIC, "alice", List.of()))))
			.doesNotThrowAnyException();
	}

}
