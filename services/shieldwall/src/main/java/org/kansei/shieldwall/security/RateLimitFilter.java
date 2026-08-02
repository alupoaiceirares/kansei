package org.kansei.shieldwall.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP rate limit on endpoints that let an unauthenticated caller trigger an email send (register, resend verification, request password reset)
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/verify-email/resend",
            "/api/auth/password-reset/request"
    );

    @Value("${app.rate-limit.max-requests:5}")
    private int maxRequests;

    @Value("${app.rate-limit.window-minutes:15}")
    private long windowMinutes;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + "|" + request.getRequestURI();
        Window window = windows.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.isExpired(now, windowMinutes)) {
                return new Window(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > maxRequests) {
            long retryAfterSeconds = Math.max(1, windowMinutes * 60 - Duration.between(window.start, Instant.now()).toSeconds());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,\"message\":\"Too many requests - try again later\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    // Evicts expired windows so the map doesn't grow forever across long uptime.
    @Scheduled(fixedRate = 3_600_000)
    public void cleanup() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowMinutes));
    }

    private String clientIp(HttpServletRequest request) {
        // Set by Spring Cloud Gateway when traffic is routed through it - getRemoteAddr()
        // would otherwise see the gateway's IP for every caller instead of the real client.
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final Instant start;
        private final AtomicInteger count = new AtomicInteger(1);

        private Window(Instant start) {
            this.start = start;
        }

        private boolean isExpired(Instant now, long windowMinutes) {
            return now.isAfter(start.plusSeconds(windowMinutes * 60));
        }
    }
}
