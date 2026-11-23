package ua.khpi.websockets.realtime;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Receives every spec from this instance's queue (no matter which instance published it) and hands it to the
 * processor named in the "processor" header. Unknown or missing processors are dropped, never guessed.
 */
@Component
class TodoEventListener {

	private static final Logger log = LoggerFactory.getLogger(TodoEventListener.class);

	private final Map<String, SpecProcessor> processors;
	private final JsonMapper jsonMapper;

	TodoEventListener(List<SpecProcessor> processors, JsonMapper jsonMapper) {
		this.processors = processors.stream().collect(Collectors.toMap(SpecProcessor::name, Function.identity()));
		this.jsonMapper = jsonMapper;
	}

	@RabbitListener(queues = "#{todoEventsQueue.name}")
	void onEvent(Message message) {
		String name = message.getMessageProperties().getHeader(SpecProcessor.HEADER);
		SpecProcessor processor = name == null ? null : processors.get(name);
		if (processor == null) {
			log.warn("Ignoring todo event with unknown processor: {}", name);
			return;
		}
		processor.process(jsonMapper.readTree(message.getBody()));
	}

}
