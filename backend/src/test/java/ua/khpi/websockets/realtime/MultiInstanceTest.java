package ua.khpi.websockets.realtime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ua.khpi.websockets.WebSocketsApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two backend instances (Alpha and Bravo) sharing one database and one RabbitMQ, like behind the load balancer.
 * <p>
 * In phase 3 a change made through Alpha never reached a browser connected to Bravo: each instance pushed
 * only to its own sockets. PHASE 4 FIXES IT: every instance publishes to the broker's fanout exchange and
 * every instance receives every event, so the change reaches sockets on all instances.
 */
class MultiInstanceTest {

	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");
	static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-alpine");
	static ConfigurableApplicationContext alpha;
	static ConfigurableApplicationContext bravo;

	final HttpClient http = HttpClient.newHttpClient();
	final List<WebSocketSession> sessions = new ArrayList<>();

	@BeforeAll
	static void startInfrastructureAndTwoInstances() {
		postgres.start();
		rabbitmq.start();
		alpha = startInstance("Alpha");
		bravo = startInstance("Bravo");
	}

	@AfterEach
	void closeClients() throws Exception {
		for (WebSocketSession session : sessions) {
			session.close();
		}
		http.close();
	}

	@AfterAll
	static void stopEverything() {
		if (bravo != null) bravo.close();
		if (alpha != null) alpha.close();
		rabbitmq.stop();
		postgres.stop();
	}

	@Test
	void publicChangeMadeOnOneInstanceReachesSocketsOnAllInstances() throws Exception {
		String aliceToken = login(alpha, "alice");
		String bobToken = login(bravo, "bob"); // the same JWT secret, so either instance could issue it

		BlockingQueue<String> alice = connect(alpha, aliceToken, "Alpha");
		BlockingQueue<String> bob = connect(bravo, bobToken, "Bravo");

		createList(alpha, aliceToken, "Made on Alpha", "PUBLIC");

		assertListArrived(alice.poll(5, TimeUnit.SECONDS), "Made on Alpha");
		// Phase 3 failed exactly here: Bob's socket lives on Bravo. Now Bravo gets the event from RabbitMQ.
		assertListArrived(bob.poll(5, TimeUnit.SECONDS), "Made on Alpha");
	}

	@Test
	void privateChangeReachesTheOwnersTabsOnAnyInstanceButNobodyElse() throws Exception {
		String aliceToken = login(alpha, "alice");

		BlockingQueue<String> aliceOnAlpha = connect(alpha, aliceToken, "Alpha");
		BlockingQueue<String> aliceOnBravo = connect(bravo, aliceToken, "Bravo");
		BlockingQueue<String> bobOnBravo = connect(bravo, login(bravo, "bob"), "Bravo");

		createList(alpha, aliceToken, "Alice only", "PRIVATE");

		assertListArrived(aliceOnAlpha.poll(5, TimeUnit.SECONDS), "Alice only");
		assertListArrived(aliceOnBravo.poll(5, TimeUnit.SECONDS), "Alice only");
		assertThat(bobOnBravo.poll(1, TimeUnit.SECONDS)).as("Bob must not hear about Alice's private list").isNull();
	}

	private static void assertListArrived(String message, String title) {
		assertThat(message).as("expected a LIST_CHANGED push").isNotNull();
		assertThat((String) JsonPath.read(message, "$.type")).isEqualTo("LIST_CHANGED");
		assertThat((String) JsonPath.read(message, "$.list.title")).isEqualTo(title);
	}

	private static ConfigurableApplicationContext startInstance(String name) {
		return new SpringApplicationBuilder(WebSocketsApplication.class).run(
				"--server.port=0",
				"--app.instance.name=" + name,
				"--spring.datasource.url=" + postgres.getJdbcUrl(),
				"--spring.datasource.username=" + postgres.getUsername(),
				"--spring.datasource.password=" + postgres.getPassword(),
				"--spring.rabbitmq.host=" + rabbitmq.getHost(),
				"--spring.rabbitmq.port=" + rabbitmq.getAmqpPort(),
				"--spring.rabbitmq.username=" + rabbitmq.getAdminUsername(),
				"--spring.rabbitmq.password=" + rabbitmq.getAdminPassword());
	}

	private static int port(ConfigurableApplicationContext instance) {
		return Integer.parseInt(instance.getEnvironment().getRequiredProperty("local.server.port"));
	}

	private String login(ConfigurableApplicationContext instance, String username) throws Exception {
		String body = post(instance, "/api/auth/login", null,
				"{\"username\": \"%s\", \"password\": \"demo1234\"}".formatted(username));
		return JsonPath.read(body, "$.token");
	}

	private void createList(ConfigurableApplicationContext instance, String token, String title, String visibility)
			throws Exception {
		post(instance, "/api/lists", token,
				"{\"title\": \"%s\", \"visibility\": \"%s\"}".formatted(title, visibility));
	}

	private String post(ConfigurableApplicationContext instance, String path, String token, String json)
			throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port(instance) + path))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(path).isBetween(200, 299);
		return response.body();
	}

	private BlockingQueue<String> connect(ConfigurableApplicationContext instance, String token, String expectedName)
			throws Exception {
		BlockingQueue<String> received = new LinkedBlockingQueue<>();
		WebSocketSession session = new StandardWebSocketClient()
			.execute(new TextWebSocketHandler() {
				@Override
				protected void handleTextMessage(WebSocketSession session, TextMessage message) {
					received.add(message.getPayload());
				}
			}, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port(instance) + "/api/ws?token=" + token))
			.get(5, TimeUnit.SECONDS);
		sessions.add(session);

		String hello = received.poll(5, TimeUnit.SECONDS);
		assertThat(hello).isNotNull();
		assertThat((String) JsonPath.read(hello, "$.instance")).isEqualTo(expectedName);
		return received;
	}

}
