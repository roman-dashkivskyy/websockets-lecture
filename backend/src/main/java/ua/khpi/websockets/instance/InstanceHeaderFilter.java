package ua.khpi.websockets.instance;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds "X-Instance-Name" to every response (even 401s), so the UI can show which backend instance
 * answered behind the load balancer. Runs before the security filters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceHeaderFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Instance-Name";

	private final InstanceProperties properties;

	public InstanceHeaderFilter(InstanceProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		response.setHeader(HEADER, properties.name());
		chain.doFilter(request, response);
	}

}
