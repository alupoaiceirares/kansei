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
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * Verifies signature + expiry only. No DB access here, so no credentials_version check like shieldwall does - a password/email change won't invalidate a token at the gateway until it naturally expires
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/verify-email",
            "/api/auth/password-reset"
    );

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(stripUserIdHeader(exchange));
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring("Bearer ".length());
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }

        return chain.filter(withUserIdHeader(exchange, claims.getSubject()));
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

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }
}
