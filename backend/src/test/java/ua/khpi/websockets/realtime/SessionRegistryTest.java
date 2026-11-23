package ua.khpi.websockets.realtime;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionRegistryTest {

	private final SessionRegistry registry = new SessionRegistry(JsonMapper.builder().build());

	record Ping(String text) {
	}

	@Test
	void notifyUserReachesAllTabsOfThatUserOnly() throws IOException {
		WebSocketSession aliceTab1 = session("a1");
		WebSocketSession aliceTab2 = session("a2");
		WebSocketSession bob = session("b1");
		registry.add("alice", aliceTab1);
		registry.add("alice", aliceTab2);
		registry.add("bob", bob);

		registry.notifyUser("alice", new Ping("hi"));

		verify(aliceTab1).sendMessage(new TextMessage("{\"text\":\"hi\"}"));
		verify(aliceTab2).sendMessage(new TextMessage("{\"text\":\"hi\"}"));
		verify(bob, never()).sendMessage(any());
	}

	@Test
	void notifyAllReachesEveryone() throws IOException {
		WebSocketSession alice = session("a1");
		WebSocketSession bob = session("b1");
		registry.add("alice", alice);
		registry.add("bob", bob);

		registry.notifyAll(new Ping("all"));

		verify(alice).sendMessage(any());
		verify(bob).sendMessage(any());
	}

	@Test
	void closedSessionIsForgotten() {
		registry.add("alice", session("a1"));
		registry.add("alice", session("a2"));

		registry.remove("alice", "a1");
		assertThat(registry.sessionCount("alice")).isEqualTo(1);

		registry.remove("alice", "a2");
		assertThat(registry.sessionCount("alice")).isZero();
	}

	@Test
	void brokenSessionIsDroppedAndOthersStillGetTheMessage() throws IOException {
		WebSocketSession broken = session("a1");
		WebSocketSession healthy = session("a2");
		doThrow(new IOException("connection reset")).when(broken).sendMessage(any());
		registry.add("alice", broken);
		registry.add("alice", healthy);

		registry.notifyUser("alice", new Ping("hi"));

		verify(healthy).sendMessage(any());
		verify(broken).close(any());
		assertThat(registry.sessionCount("alice")).isEqualTo(1);
	}

	private static WebSocketSession session(String id) {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn(id);
		return session;
	}

}
