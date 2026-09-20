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
import java.util.regex.Pattern;

/**
 * Verifies signature + expiry, and credentials_version (the ver claim) against Redis, shieldwall writes the current version there on every password/email change, so a token issued before such a change is rejected here instead of surviving until natural expiry
 * Still no DB access, Redis is a mirror shieldwall pushes to, not a source this filter reads through to Postgres
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String DEFAULT_ROLE = "USER";
    private static final String CREDENTIALS_VERSION_KEY_PREFIX = "shieldwall:credentials-version:";
    private static final String BLACKLISTED_JTI_KEY_PREFIX = "shieldwall:blacklisted-jti:";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/verify-email",
            "/api/auth/password-reset",
            // Ticket-authenticated instead of JWT, EventSource can't send an Authorization header,
            // the ticket itself (burned server-side against Redis) carries the identity check
            "/wirehood/downloads/stream",
            // A logged-out or expired-token visitor should still see "wirehood is down", not a 401
            "/health/"
    );

    // A plain <img src> can't send an Authorization header (same problem as EventSource above),
    // and these are non-sensitive, platform-shared cosmetic images anyway - no ownership check
    // exists server-side for them either (see TrackService.getThumbnail). Path shape is fixed
    // (only the trackId segment varies), so a prefix match alone would also wrongly expose sibling
    // routes like GET/PATCH/DELETE /wirehood/tracks/{trackId} - matched by exact shape instead.
    private static final Pattern THUMBNAIL_PATH = Pattern.compile("^/wirehood/tracks/[0-9a-fA-F-]{36}/thumbnail$");

    // Same reasoning for tailwind's aircraft photos: shared cosmetic images with no per-user data, loaded
    // by a plain <img src>. Exact shapes only, so the sibling /photo/info and /aircraft-types/{id} routes
    // (and everything under /tailwind/admin) still need a token.
    private static final Pattern AIRCRAFT_PHOTO_PATH = Pattern.compile("^/tailwind/aircraft-types/\\d+/photo$|^/tailwind/aircraft-families/photo$");

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
            return chain.filter(stripSpoofableHeaders(exchange));
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
                .zipWith(isNotBlacklisted(claims))
                .flatMap(checks -> (checks.getT1() && checks.getT2())
                        ? chain.filter(withIdentityHeaders(exchange, claims.getSubject(), extractRole(claims)))
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

    // Redis is the only store for a blacklisted jti, no Postgres-backed backstop the way ver has, so this check lives only here
    // Missing jti (token issued before this claim existed) or Redis being unreachable both fail OPEN, same philosophy as above
    private Mono<Boolean> isNotBlacklisted(Claims claims) {
        String jti = claims.getId();
        if (jti == null) {
            return Mono.just(true);
        }
        return redisTemplate.hasKey(BLACKLISTED_JTI_KEY_PREFIX + jti)
                .map(blacklisted -> !blacklisted)
                .defaultIfEmpty(true)
                .onErrorReturn(true);
    }

    // Missing role claim (pre-existing tokens issued before this claim existed) defaults to USER, never ADMIN
    private static String extractRole(Claims claims) {
        String role = claims.get("role", String.class);
        return role != null ? role : DEFAULT_ROLE;
    }

    private static boolean matchesOrUnparseable(String storedVersion, int tokenVersion) {
        try {
            return Integer.parseInt(storedVersion) == tokenVersion;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    // One below RateLimitFilter's HIGHEST_PRECEDENCE, the coarse per-IP throttle should reject spam before this filter spends any crypto work verifying a signature
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith)
                || THUMBNAIL_PATH.matcher(path).matches()
                || AIRCRAFT_PHOTO_PATH.matcher(path).matches();
    }

    /**
     * Client-supplied X-User-Id/X-User-Role are untrusted input - always dropped before forwarding downstream, whether the request ends up authenticated or not, so nothing can spoof them
     */
    private ServerWebExchange stripSpoofableHeaders(ServerWebExchange exchange) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange, String userId, String role) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(USER_ID_HEADER, userId);
                    headers.set(USER_ROLE_HEADER, role);
                })
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
