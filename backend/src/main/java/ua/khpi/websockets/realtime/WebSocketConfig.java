package ua.khpi.websockets.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * One raw WebSocket endpoint: ws://host/api/ws?token=...
 * The default origin check (same origin only) stays on, so no CORS setup is needed.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final TodoSocketHandler handler;
	private final TokenHandshakeInterceptor tokenInterceptor;

	public WebSocketConfig(TodoSocketHandler handler, TokenHandshakeInterceptor tokenInterceptor) {
		this.handler = handler;
		this.tokenInterceptor = tokenInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/api/ws").addInterceptors(tokenInterceptor);
	}

}
