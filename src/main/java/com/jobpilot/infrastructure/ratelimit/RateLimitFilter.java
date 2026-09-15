package com.jobpilot.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.auth.security.UserPrincipal;
import com.jobpilot.common.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limiter. Identity is the authenticated user id when
 * present, otherwise the client IP ({@code X-Forwarded-For} first hop, then
 * {@code X-Real-IP}, then {@code remoteAddr}).
 *
 * <p>{@code /api/auth/**} uses the stricter auth limit (per IP). Actuator health
 * and {@code /api/v1/ping} are excluded.
 *
 * <p>Must run after {@code JwtAuthenticationFilter} so {@link SecurityContextHolder}
 * is populated. Registered only on the security chain (servlet registration disabled).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final String RETRY_AFTER = "Retry-After";

    private final RateLimitStore rateLimitStore;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimitStore rateLimitStore,
            RateLimitProperties properties,
            ObjectMapper objectMapper
    ) {
        this.rateLimitStore = rateLimitStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return isExcluded(path);
    }

    static boolean isExcluded(String path) {
        if (path == null) {
            return false;
        }
        return path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.equals("/api/v1/ping");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean authEndpoint = path != null && path.startsWith("/api/auth/");

        String bucket;
        int limit;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            bucket = "user:" + principal.getId();
            limit = properties.getDefaultLimit();
        } else {
            bucket = "ip:" + clientIp(request);
            limit = authEndpoint ? properties.getAuthLimit() : properties.getDefaultLimit();
        }

        String key = (authEndpoint ? "auth:" : "api:") + bucket;
        RateLimitResult result = rateLimitStore.increment(key, properties.getWindow());
        if (result.count() > limit) {
            writeTooManyRequests(request, response, result.retryAfterSeconds());
            return;
        }
        filterChain.doFilter(request, response);
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null && !remote.isBlank() ? remote : "unknown";
    }

    private void writeTooManyRequests(
            HttpServletRequest request,
            HttpServletResponse response,
            long retryAfterSeconds
    ) throws IOException {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Rate limit exceeded",
                request.getRequestURI()
        );
        response.setStatus(status.value());
        response.setHeader(RETRY_AFTER, String.valueOf(Math.max(1L, retryAfterSeconds)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
