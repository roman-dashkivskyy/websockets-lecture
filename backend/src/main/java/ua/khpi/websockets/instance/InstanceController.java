package ua.khpi.websockets.instance;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instance")
public class InstanceController {

	private final InstanceProperties properties;
	private final int port;
	private final String hostname = resolveHostname();
	private final Instant startedAt = Instant.now();

	public InstanceController(InstanceProperties properties, @Value("${server.port:8080}") int port) {
		this.properties = properties;
		this.port = port;
	}

	@GetMapping
	public InstanceInfo instance() {
		return new InstanceInfo(properties.name(), hostname, port, startedAt);
	}

	private static String resolveHostname() {
		try {
			return InetAddress.getLocalHost().getHostName();
		}
		catch (UnknownHostException ex) {
			return "unknown";
		}
	}

}
