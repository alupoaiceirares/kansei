package org.kansei.controltower.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies signature + expiry, and credentials_version (the ver claim) against Redis, shieldwall writes the current version there on every password/email change, so a token issued before such a change is rejected here instead of surviving until natural expiry
 * Still no DB access, Redis is a mirror shieldwall pushes to, not a source this filter reads through to Postgres
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String CREDENTIALS_VERSION_KEY_PREFIX = "shieldwall:credentials-version:";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/verify-email",
            "/api/auth/password-reset"
    );

    private final SecretKey signingKey;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(
            @Value("${jwt.secret}") String secret,
            ObjectMapper objectMapper,
            ReactiveStringRedisTemplate redisTemplate
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(stripUserIdHeader(exchange));
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring("Bearer ".length());
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "Invalid or expired token");
        }

        return hasCurrentCredentialsVersion(claims)
                .flatMap(current -> current
                        ? chain.filter(withUserIdHeader(exchange, claims.getSubject()))
                        : unauthorized(exchange, "Invalid or expired token"));
    }

    // A missing ver claim, a missing Redis key (never bumped, or Redis flushed/restarted), or Redis being unreachable all fail OPEN, not closed: there's no DB fallback at this layer, shieldwall's own credentials_version check remains the
    // authoritative backstop regardless
    private Mono<Boolean> hasCurrentCredentialsVersion(Claims claims) {
        Integer tokenVersion = claims.get("ver", Integer.class);
        if (tokenVersion == null) {
            return Mono.just(true);
        }
        return redisTemplate.opsForValue().get(CREDENTIALS_VERSION_KEY_PREFIX + claims.getSubject())
                .map(storedVersion -> matchesOrUnparseable(storedVersion, tokenVersion))
                .defaultIfEmpty(true)
                .onErrorReturn(true);
    }

    private static boolean matchesOrUnparseable(String storedVersion, int tokenVersion) {
        try {
            return Integer.parseInt(storedVersion) == tokenVersion;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Client-supplied X-User-Id is untrusted input - always dropped before forwarding downstream, whether the request ends up authenticated or not, so nothing can spoof it
     */
    private ServerWebExchange stripUserIdHeader(ServerWebExchange exchange) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(USER_ID_HEADER))
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private ServerWebExchange withUserIdHeader(ServerWebExchange exchange, String userId) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> headers.set(USER_ID_HEADER, userId))
                .build();
        return exchange.mutate().request(mutated).build();
    }

    // Same {timestamp, status, message} shape as shieldwall's GlobalExceptionHandler, so clients handle errors uniformly across services
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("message", message);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"status\":401,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
