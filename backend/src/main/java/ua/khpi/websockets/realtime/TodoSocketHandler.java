package ua.khpi.websockets.realtime;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ua.khpi.websockets.instance.InstanceProperties;

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

	/** First message on every socket: {"type": "CONNECTED", "instance": "Bravo"}, so the UI can show where it lives. */
	record ConnectedMessage(String type, String instance) {
		ConnectedMessage(String instance) {
			this("CONNECTED", instance);
		}
	}

	private final SessionRegistry registry;
	private final InstanceProperties instance;

	public TodoSocketHandler(SessionRegistry registry, InstanceProperties instance) {
		this.registry = registry;
		this.instance = instance;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		// sendMessage() is not thread-safe; the decorator serializes concurrent sends to one session.
		WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
		String username = username(session);
		// say hello before registering, so CONNECTED is always the first message
		registry.notifySession(username, safe, new ConnectedMessage(instance.name()));
		if (safe.isOpen()) { // the greeting may have failed and closed it
			registry.add(username, safe);
		}
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
