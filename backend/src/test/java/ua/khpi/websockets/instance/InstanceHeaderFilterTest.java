package ua.khpi.websockets.instance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import ua.khpi.websockets.IntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class InstanceHeaderFilterTest {

	@Autowired
	MockMvc mvc;

	@Test
	void everyResponseNamesTheInstanceEvenWhenUnauthorized() throws Exception {
		mvc.perform(get("/api/instance"))
			.andExpect(status().isOk())
			.andExpect(header().string(InstanceHeaderFilter.HEADER, "Alpha"));
		mvc.perform(get("/api/lists"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(InstanceHeaderFilter.HEADER, "Alpha"));
	}

}
