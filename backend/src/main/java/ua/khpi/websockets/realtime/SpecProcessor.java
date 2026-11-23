package ua.khpi.websockets.realtime;

import tools.jackson.databind.JsonNode;

/**
 * What a broker message asks an instance to do. The message is a small "spec": the processor's name in the
 * "processor" header and its parameters (ids) in the body. The processor re-reads the data it needs from the
 * database and pushes the result to this instance's sockets.
 */
interface SpecProcessor {

	/** Header name that selects the processor. */
	String HEADER = "processor";

	String LIST_CHANGED = "LIST_CHANGED";
	String LIST_DELETED = "LIST_DELETED";
	String LISTS_REPLACED = "LISTS_REPLACED";

	/** Value of the "processor" header this processor handles. */
	String name();

	void process(JsonNode params);

}
