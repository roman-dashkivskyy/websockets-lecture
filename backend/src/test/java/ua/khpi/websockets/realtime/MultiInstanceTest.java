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
import ua.khpi.websockets.WebSocketsApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two backend instances (Alpha and Bravo) sharing one database, like behind the load balancer.
 * <p>
 * PHASE 3, KNOWN BREAKAGE: each instance pushes only to the sockets it holds itself, so a change made
 * through Alpha never reaches a browser connected to Bravo. Phase 4 (message broker) fixes this, and
 * this test then flips its last assertion.
 */
class MultiInstanceTest {

	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");
	static ConfigurableApplicationContext alpha;
	static ConfigurableApplicationContext bravo;

	final HttpClient http = HttpClient.newHttpClient();
	final List<WebSocketSession> sessions = new ArrayList<>();

	@BeforeAll
	static void startDatabaseAndTwoInstances() {
		postgres.start();
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
		postgres.stop();
	}

	@Test
	void changeMadeOnOneInstanceDoesNotReachSocketsOnAnother() throws Exception {
		String aliceToken = login(alpha, "alice");
		String bobToken = login(bravo, "bob"); // the same JWT secret, so either instance could issue it

		BlockingQueue<String> alice = connect(alpha, aliceToken, "Alpha");
		BlockingQueue<String> bob = connect(bravo, bobToken, "Bravo");

		createPublicList(alpha, aliceToken, "Made on Alpha");

		String aliceGot = alice.poll(5, TimeUnit.SECONDS);
		assertThat(aliceGot).as("Alice's socket is on Alpha, where the change happened").isNotNull();
		assertThat((String) JsonPath.read(aliceGot, "$.list.title")).isEqualTo("Made on Alpha");

		// Phase 3: Bob's socket lives on Bravo, and Bravo never hears about the change.
		assertThat(bob.poll(1, TimeUnit.SECONDS)).as("phase 3 breakage: Bob on Bravo misses Alpha's change").isNull();
	}

	private static ConfigurableApplicationContext startInstance(String name) {
		return new SpringApplicationBuilder(WebSocketsApplication.class).run(
				"--server.port=0",
				"--app.instance.name=" + name,
				"--spring.datasource.url=" + postgres.getJdbcUrl(),
				"--spring.datasource.username=" + postgres.getUsername(),
				"--spring.datasource.password=" + postgres.getPassword());
	}

	private static int port(ConfigurableApplicationContext instance) {
		return Integer.parseInt(instance.getEnvironment().getRequiredProperty("local.server.port"));
	}

	private String login(ConfigurableApplicationContext instance, String username) throws Exception {
		String body = post(instance, "/api/auth/login", null,
				"{\"username\": \"%s\", \"password\": \"demo1234\"}".formatted(username));
		return JsonPath.read(body, "$.token");
	}

	private void createPublicList(ConfigurableApplicationContext instance, String token, String title) throws Exception {
		post(instance, "/api/lists", token, "{\"title\": \"%s\", \"visibility\": \"PUBLIC\"}".formatted(title));
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
