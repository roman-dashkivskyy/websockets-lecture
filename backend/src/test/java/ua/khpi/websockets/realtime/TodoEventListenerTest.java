package ua.khpi.websockets.realtime;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TodoEventListenerTest {

	private final SpecProcessor listChanged = processor("LIST_CHANGED");
	private final SpecProcessor listDeleted = processor("LIST_DELETED");
	private final TodoEventListener listener = new TodoEventListener(List.of(listChanged, listDeleted),
			JsonMapper.builder().build());

	@Test
	void specGoesToTheProcessorNamedInTheHeader() {
		listener.onEvent(message("LIST_CHANGED", "{\"listId\":42}"));

		verify(listChanged).process(argThat((JsonNode params) -> params.get("listId").asLong() == 42));
		verify(listDeleted, never()).process(any());
	}

	@Test
	void unknownOrMissingProcessorIsDroppedNeverGuessed() {
		listener.onEvent(message("SOMETHING_ELSE", "{}"));
		listener.onEvent(message(null, "{}"));

		verify(listChanged, never()).process(any());
		verify(listDeleted, never()).process(any());
	}

	@Test
	void malformedSpecFailsSoTheContainerRejectsItWithoutRequeue() {
		// the exception makes the listener container reject the message (default-requeue-rejected: false)
		assertThatThrownBy(() -> listener.onEvent(message("LIST_CHANGED", "not json")))
			.isInstanceOf(RuntimeException.class);
		verify(listChanged, never()).process(any());
	}

	private static SpecProcessor processor(String name) {
		SpecProcessor processor = mock(SpecProcessor.class);
		when(processor.name()).thenReturn(name);
		return processor;
	}

	private static Message message(String processor, String json) {
		MessageBuilder builder = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8));
		if (processor != null) {
			builder.setHeader(SpecProcessor.HEADER, processor);
		}
		return builder.build();
	}

}
