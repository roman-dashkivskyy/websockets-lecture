package ua.khpi.websockets.auth;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record LoginResponse(String token, String username, String displayName, Instant expiresAt) {
	}

	public record Me(String username, String displayName) {
	}

	private final AuthenticationManager authenticationManager;
	private final AppUserRepository users;
	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;

	public AuthController(AuthenticationManager authenticationManager, AppUserRepository users, JwtEncoder jwtEncoder,
			JwtProperties jwtProperties) {
		this.authenticationManager = authenticationManager;
		this.users = users;
		this.jwtEncoder = jwtEncoder;
		this.jwtProperties = jwtProperties;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		try {
			authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
		}
		catch (AuthenticationException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password");
		}

		AppUser user = users.findByUsername(request.username()).orElseThrow();
		Instant now = Instant.now();
		Instant expiresAt = now.plus(jwtProperties.ttl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(user.getUsername())
			.claim("name", user.getDisplayName())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.build();
		String token = jwtEncoder
			.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
		return new LoginResponse(token, user.getUsername(), user.getDisplayName(), expiresAt);
	}

	@GetMapping("/me")
	public Me me(@AuthenticationPrincipal Jwt jwt) {
		return new Me(jwt.getSubject(), jwt.getClaimAsString("name"));
	}

}
