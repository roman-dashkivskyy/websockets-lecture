package ua.khpi.websockets.realtime;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Connection lifecycle: remember the session when it opens, forget it when it closes.
 * In this demo the server only pushes, so incoming text messages are ignored
 * (TextWebSocketHandler.handleTextMessage does nothing by default).
 */
@Component
public class TodoSocketHandler extends TextWebSocketHandler {

	/** Give up on a client that can't keep up: 5 s per send, at most 1 MB waiting in its buffer. */
	private static final int SEND_TIME_LIMIT_MS = 5_000;
	private static final int BUFFER_SIZE_LIMIT = 1024 * 1024;

	private final SessionRegistry registry;

	public TodoSocketHandler(SessionRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		// sendMessage() is not thread-safe; the decorator serializes concurrent sends to one session.
		registry.add(username(session),
				new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		registry.remove(username(session), session.getId());
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
		session.close(CloseStatus.SERVER_ERROR);
	}

	private static String username(WebSocketSession session) {
		return (String) session.getAttributes().get(TokenHandshakeInterceptor.USERNAME);
	}

}
