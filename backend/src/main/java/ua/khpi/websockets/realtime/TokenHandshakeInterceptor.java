package ua.khpi.websockets.realtime;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Checks the user before the connection is upgraded to a WebSocket.
 * The browser's WebSocket API can't send an Authorization header, so the JWT comes as ?token=...
 * Such URLs must stay out of logs: our nginx access log drops the query string, but its error log
 * (e.g. on a 502) still prints the full URL.
 * The token is checked only here, once: an open socket is not closed when the token later expires.
 */
@Component
public class TokenHandshakeInterceptor implements HandshakeInterceptor {

	static final String USERNAME = "username";

	private final JwtDecoder jwtDecoder;

	public TokenHandshakeInterceptor(JwtDecoder jwtDecoder) {
		this.jwtDecoder = jwtDecoder;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
			Map<String, Object> attributes) {
		String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
		if (token == null || token.isBlank()) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}
		try {
			String username = jwtDecoder.decode(token).getSubject();
			if (username == null || username.isBlank()) {
				response.setStatusCode(HttpStatus.UNAUTHORIZED);
				return false;
			}
			attributes.put(USERNAME, username);
			return true;
		}
		catch (JwtException ex) { // bad signature, expired, malformed
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
			Exception exception) {
	}

}
