package ua.khpi.websockets;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** Same image as in docker-compose; Spring Boot wires the datasource to it automatically. */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:18-alpine");
	}

	/** A real RabbitMQ, so the tests go through the same broker path as production. */
	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitmq() {
		return new RabbitMQContainer("rabbitmq:4-alpine");
	}

}
