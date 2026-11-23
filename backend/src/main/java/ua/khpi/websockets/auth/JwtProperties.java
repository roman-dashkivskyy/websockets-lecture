package ua.khpi.websockets.auth;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret HMAC key shared by all backend instances (at least 32 characters for HS256)
 * @param ttl    how long an issued token stays valid
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotNull @Size(min = 32) String secret, @NotNull Duration ttl) {
}
