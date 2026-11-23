package ua.khpi.websockets.realtime;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Base64UrlNamingStrategy;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.khpi.websockets.instance.InstanceProperties;

/**
 * RabbitMQ topology: ONE fanout exchange, ONE queue per backend instance.
 * <p>
 * A fanout exchange copies every message into every queue bound to it, so every instance hears every event.
 * (A single shared work queue would be the classic mistake: each message would reach only ONE instance.)
 * Each instance's queue is exclusive and auto-delete: it disappears when the instance stops.
 */
@Configuration
public class BrokerConfig {

	public static final String EXCHANGE = "todo.events";

	@Bean
	FanoutExchange todoEventsExchange() {
		return new FanoutExchange(EXCHANGE);
	}

	/** Named like "todo.events.Bravo-k3J9..." so you can tell the queues apart in the RabbitMQ UI. */
	@Bean
	AnonymousQueue todoEventsQueue(InstanceProperties instance) {
		return new AnonymousQueue(new Base64UrlNamingStrategy(EXCHANGE + "." + instance.name() + "-"));
	}

	@Bean
	Binding todoEventsBinding(AnonymousQueue todoEventsQueue, FanoutExchange todoEventsExchange) {
		return BindingBuilder.bind(todoEventsQueue).to(todoEventsExchange);
	}

}
