package ua.khpi.websockets.instance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import ua.khpi.websockets.IntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class InstanceControllerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void returnsConfiguredInstanceNameWithoutLogin() throws Exception {
		mvc.perform(get("/api/instance"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Alpha"))
			.andExpect(jsonPath("$.hostname").isNotEmpty())
			.andExpect(jsonPath("$.port").isNumber())
			.andExpect(jsonPath("$.startedAt").isNotEmpty());
	}

	@Test
	void readinessProbeIsUp() throws Exception {
		// docker-compose uses this endpoint as the backend healthcheck
		mvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

}
