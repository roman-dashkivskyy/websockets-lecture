package ua.khpi.websockets.instance;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-instance settings. Every running copy of the backend gets its own values,
 * e.g. via the {@code INSTANCE_NAME} environment variable.
 *
 * @param name human-friendly name of this backend instance
 */
@Validated
@ConfigurationProperties(prefix = "app.instance")
public record InstanceProperties(@NotBlank String name) {
}
