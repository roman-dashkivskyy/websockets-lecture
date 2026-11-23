package ua.khpi.websockets.realtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ua.khpi.websockets.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real WebSocket clients against the running app: a change made over HTTP
 * arrives as a push message at every browser that may see it, and nowhere else.
 */
@IntegrationTest
class WebSocketIntegrationTest {

	@LocalServerPort
	int port;

	@Autowired
	MockMvc mvc;

	private final StandardWebSocketClient client = new StandardWebSocketClient();
	private final List<WebSocketSession> openSessions = new ArrayList<>();

	@AfterEach
	void closeSessions() throws Exception {
		for (WebSocketSession session : openSessions) {
			session.close();
		}
	}

	@Test
	void handshakeWithoutValidTokenIsRejected() {
		assertThrows(ExecutionException.class, () -> connect(null));
		assertThrows(ExecutionException.class, () -> connect("not-a-jwt"));
	}

	@Test
	void firstMessageNamesTheInstanceTheSocketLivesOn() throws Exception {
		// connect() itself waits for {"type":"CONNECTED","instance":"Alpha"} and fails otherwise
		BlockingQueue<String> alice = connect(login("alice"));
		assertThat(alice.poll(300, TimeUnit.MILLISECONDS)).as("nothing else is sent on connect").isNull();
	}

	@Test
	void browserFromAnotherSiteIsRejectedEvenWithValidToken() throws Exception {
		String token = login("alice");
		// browsers always send Origin; the default same-origin check must accept ours and reject others
		connect(token, "http://localhost:" + port);
		assertThrows(ExecutionException.class, () -> connect(token, "http://evil.example"));
	}

	@Test
	void publicChangeReachesEveryoneIncludingTheAuthor() throws Exception {
		String aliceToken = login("alice");
		BlockingQueue<String> alice = connect(aliceToken);
		BlockingQueue<String> bob = connect(login("bob"));

		long listId = createList(aliceToken, "Live list", "PUBLIC");
		assertListChanged(alice.poll(5, TimeUnit.SECONDS), listId, "Live list");
		assertListChanged(bob.poll(5, TimeUnit.SECONDS), listId, "Live list");

		long itemId = addItem(aliceToken, listId, "Ticked live");
		alice.poll(5, TimeUnit.SECONDS);
		// the snapshot taken right after adding must contain the new item exactly once, with its id
		String added = bob.poll(5, TimeUnit.SECONDS);
		assertListChanged(added, listId, "Live list");
		assertThat((List<?>) JsonPath.read(added, "$.list.items")).hasSize(1);
		assertThat((String) JsonPath.read(added, "$.list.items[0].title")).isEqualTo("Ticked live");
		assertThat(((Number) JsonPath.read(added, "$.list.items[0].id")).longValue()).isEqualTo(itemId);

		mvc.perform(patch("/api/items/{id}", itemId).header("Authorization", "Bearer " + aliceToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"done\": true}"))
			.andExpect(status().isOk());
		String message = bob.poll(5, TimeUnit.SECONDS);
		assertListChanged(message, listId, "Live list");
		assertThat((Boolean) JsonPath.read(message, "$.list.items[0].done")).isTrue();

		mvc.perform(delete("/api/lists/{id}", listId).header("Authorization", "Bearer " + aliceToken))
			.andExpect(status().isNoContent());
		String deleted = bob.poll(5, TimeUnit.SECONDS);
		assertThat((String) JsonPath.read(deleted, "$.type")).isEqualTo("LIST_DELETED");
		assertThat(((Number) JsonPath.read(deleted, "$.listId")).longValue()).isEqualTo(listId);
	}

	@Test
	void privateChangeReachesOnlyTheOwnersTabs() throws Exception {
		String aliceToken = login("alice");
		BlockingQueue<String> aliceTab1 = connect(aliceToken);
		BlockingQueue<String> aliceTab2 = connect(aliceToken);
		BlockingQueue<String> bob = connect(login("bob"));

		long listId = createList(aliceToken, "Diary", "PRIVATE");

		assertListChanged(aliceTab1.poll(5, TimeUnit.SECONDS), listId, "Diary");
		assertListChanged(aliceTab2.poll(5, TimeUnit.SECONDS), listId, "Diary");
		assertThat(bob.poll(500, TimeUnit.MILLISECONDS)).as("Bob must not hear about Alice's private list").isNull();
	}

	@Test
	void replacingAllListsPushesOnlyWhatEachUserMaySee() throws Exception {
		String carolToken = login("carol"); // carol is used only for replace tests (see TodoControllerTest)
		BlockingQueue<String> carol = connect(carolToken);
		BlockingQueue<String> bob = connect(login("bob"));

		long oldPublic = createList(carolToken, "Carol old", "PUBLIC");
		// pushes go through the broker asynchronously: wait until both have seen it before replacing
		collectUntil(carol, m -> isListChanged(m, "Carol old"));
		collectUntil(bob, m -> isListChanged(m, "Carol old"));

		mvc.perform(put("/api/lists/mine").header("Authorization", "Bearer " + carolToken)
				.contentType(MediaType.APPLICATION_JSON).content("""
						[{"title": "Carol fresh", "visibility": "PUBLIC", "items": [{"title": "x", "done": false}]},
						 {"title": "Carol hidden", "visibility": "PRIVATE", "items": []}]"""))
			.andExpect(status().isOk());

		// Carol gets all of it, re-read from the database by the processor
		List<String> carolGot = collectUntil(carol, m -> isListChanged(m, "Carol hidden"));
		assertThat(carolGot).anyMatch(m -> isListDeleted(m, oldPublic));
		assertThat(carolGot).anyMatch(m -> isListChanged(m, "Carol fresh")
				&& "x".equals(JsonPath.read(m, "$.list.items[0].title")));

		// Bob: the old public list is gone and the new public one arrives, but never the private one
		List<String> bobGot = collectUntil(bob, m -> isListChanged(m, "Carol fresh"));
		assertThat(bobGot).anyMatch(m -> isListDeleted(m, oldPublic));
		bobGot.addAll(drain(bob, 500));
		assertThat(bobGot).noneMatch(m -> isListChanged(m, "Carol hidden"));
	}

	/** Messages received until one matches (inclusive); fails after 5 s. */
	private static List<String> collectUntil(BlockingQueue<String> queue, Predicate<String> done) throws Exception {
		List<String> got = new ArrayList<>();
		long deadline = System.currentTimeMillis() + 5_000;
		while (System.currentTimeMillis() < deadline) {
			String message = queue.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
			if (message == null) {
				break;
			}
			got.add(message);
			if (done.test(message)) {
				return got;
			}
		}
		throw new AssertionError("expected message did not arrive; got " + got);
	}

	private static List<String> drain(BlockingQueue<String> queue, long waitMillis) throws Exception {
		List<String> got = new ArrayList<>();
		String message;
		while ((message = queue.poll(waitMillis, TimeUnit.MILLISECONDS)) != null) {
			got.add(message);
		}
		return got;
	}

	private static boolean isListChanged(String message, String title) {
		return "LIST_CHANGED".equals(JsonPath.read(message, "$.type")) && title.equals(JsonPath.read(message, "$.list.title"));
	}

	private static boolean isListDeleted(String message, long listId) {
		return "LIST_DELETED".equals(JsonPath.read(message, "$.type"))
				&& ((Number) JsonPath.read(message, "$.listId")).longValue() == listId;
	}

	private BlockingQueue<String> connect(String token) throws Exception {
		return connect(token, null);
	}

	private BlockingQueue<String> connect(String token, String origin) throws Exception {
		BlockingQueue<String> received = new LinkedBlockingQueue<>();
		String query = token == null ? "" : "?token=" + token;
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		if (origin != null) {
			headers.setOrigin(origin);
		}
		WebSocketSession session = client
			.execute(new TextWebSocketHandler() {
				@Override
				protected void handleTextMessage(WebSocketSession session, TextMessage message) {
					received.add(message.getPayload());
				}
			}, headers, URI.create("ws://localhost:" + port + "/api/ws" + query))
			.get(5, TimeUnit.SECONDS);
		openSessions.add(session);

		String hello = received.poll(5, TimeUnit.SECONDS);
		assertThat(hello).as("expected the CONNECTED greeting").isNotNull();
		assertThat((String) JsonPath.read(hello, "$.type")).isEqualTo("CONNECTED");
		assertThat((String) JsonPath.read(hello, "$.instance")).isEqualTo("Alpha");
		return received;
	}

	private String login(String username) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\": \"%s\", \"password\": \"demo1234\"}".formatted(username)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private long createList(String token, String title, String visibility) throws Exception {
		String body = mvc.perform(post("/api/lists").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"%s\", \"visibility\": \"%s\"}".formatted(title, visibility)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private long addItem(String token, long listId, String title) throws Exception {
		String body = mvc.perform(post("/api/lists/{id}/items", listId).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"%s\"}".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private static void assertListChanged(String message, long listId, String title) {
		assertThat(message).as("expected a LIST_CHANGED message").isNotNull();
		assertThat((String) JsonPath.read(message, "$.type")).isEqualTo("LIST_CHANGED");
		assertThat(((Number) JsonPath.read(message, "$.list.id")).longValue()).isEqualTo(listId);
		assertThat((String) JsonPath.read(message, "$.list.title")).isEqualTo(title);
	}

}
