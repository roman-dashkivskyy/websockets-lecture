package ua.khpi.websockets.realtime;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import ua.khpi.websockets.todo.Visibility;

/** Sends a browser message to the right sockets of THIS instance: public → everybody, private → the owner only. */
@Component
class SocketRouter {

	private final SessionRegistry registry;
	private final JsonMapper jsonMapper;

	SocketRouter(SessionRegistry registry, JsonMapper jsonMapper) {
		this.registry = registry;
		this.jsonMapper = jsonMapper;
	}

	void send(Visibility visibility, String owner, Object browserMessage) {
		String json = jsonMapper.writeValueAsString(browserMessage);
		if (visibility == Visibility.PUBLIC) {
			registry.notifyAllJson(json);
		}
		else {
			registry.notifyUserJson(owner, json);
		}
	}

}
