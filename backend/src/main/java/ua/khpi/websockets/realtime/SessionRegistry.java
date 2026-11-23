package ua.khpi.websockets.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

/**
 * Open connections of THIS backend instance: username -> (session id -> session).
 * One user can have several sessions, one per browser tab.
 * It lives in memory, so another instance has its own, different registry (that's phase 3).
 */
@Component
public class SessionRegistry {

	private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

	private final Map<String, Map<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
	private final JsonMapper jsonMapper;

	public SessionRegistry(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void add(String username, WebSocketSession session) {
		// One atomic step. "computeIfAbsent(...).put(...)" would be check-then-act: a remove() of the user's
		// last tab in between drops the inner map, and the new tab would land in a map nobody reads.
		sessionsByUser.compute(username, (user, sessions) -> {
			Map<String, WebSocketSession> result = sessions != null ? sessions : new ConcurrentHashMap<>();
			result.put(session.getId(), session);
			return result;
		});
	}

	public void remove(String username, String sessionId) {
		sessionsByUser.computeIfPresent(username, (user, sessions) -> {
			sessions.remove(sessionId);
			return sessions.isEmpty() ? null : sessions; // returning null drops the user's entry
		});
	}

	/** Send to every connected user. */
	public void notifyAll(Object message) {
		TextMessage text = toJson(message);
		sessionsByUser.forEach((username, sessions) -> sessions.values().forEach(s -> send(username, s, text)));
	}

	/** Send to all tabs of one user. */
	public void notifyUser(String username, Object message) {
		Map<String, WebSocketSession> sessions = sessionsByUser.get(username);
		if (sessions != null) {
			TextMessage text = toJson(message);
			sessions.values().forEach(s -> send(username, s, text));
		}
	}

	int sessionCount(String username) {
		Map<String, WebSocketSession> sessions = sessionsByUser.get(username);
		return sessions == null ? 0 : sessions.size();
	}

	private void send(String username, WebSocketSession session, TextMessage text) {
		try {
			session.sendMessage(text);
		}
		catch (IOException | RuntimeException ex) {
			// a broken or too slow connection: drop it, the browser will notice the close
			log.warn("Dropping WebSocket session {} of {}: {}", session.getId(), username, ex.toString());
			remove(username, session.getId());
			closeQuietly(session);
		}
	}

	private TextMessage toJson(Object message) {
		return new TextMessage(jsonMapper.writeValueAsString(message));
	}

	private static void closeQuietly(WebSocketSession session) {
		try {
			session.close(CloseStatus.SERVER_ERROR);
		}
		catch (IOException | RuntimeException ignored) {
			// already gone
		}
	}

}
