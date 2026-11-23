package ua.khpi.websockets.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import ua.khpi.websockets.IntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthControllerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void loginReturnsTokenThatAuthenticatesLaterRequests() throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "alice", "password": "demo1234"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("alice"))
			.andExpect(jsonPath("$.displayName").value("Alice"))
			.andExpect(jsonPath("$.expiresAt").isNotEmpty())
			.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.token");

		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("alice"))
			.andExpect(jsonPath("$.displayName").value("Alice"));
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "alice", "password": "nope"}
						"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Wrong username or password"));
	}

	@Test
	void unknownUserIsRejectedTheSameWay() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "mallory", "password": "demo1234"}
						"""))
			.andExpect(status().isUnauthorized())
			// same message as a wrong password: the response doesn't reveal which usernames exist
			.andExpect(jsonPath("$.detail").value("Wrong username or password"));
	}

	@Test
	void requestsWithoutOrWithBadTokenAreRejected() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() throws Exception {
		// This is why every instance must share the same JWT secret.
		var otherKey = new SecretKeySpec("another-instance-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8),
				"HmacSHA256");
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject("alice")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(600))
			.build();
		String forged = NimbusJwtEncoder.withSecretKey(otherKey).algorithm(MacAlgorithm.HS256).build()
			.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();

		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
			.andExpect(status().isUnauthorized());
	}

}
