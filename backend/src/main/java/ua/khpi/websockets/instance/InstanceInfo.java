package ua.khpi.websockets.instance;

import java.time.Instant;

/**
 * What /api/instance reports about the backend instance that answered the request.
 */
public record InstanceInfo(String name, String hostname, int port, Instant startedAt) {
}
