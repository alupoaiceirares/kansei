package org.kansei.wirehood.security;

import org.kansei.wirehood.repository.WirehoodUserRepository;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Wirehood's WebFilter, covers REST and GraphQL uniformly since both run through the same WebFlux request chain, so GraphQlAuthInterceptor needs no separate copy of this check
 * Only acts when X-User-Id is present, endpoints that don't need identity never send it and pass through untouched here.
 * A user with no wirehood_users row yet (never opted in) is NOT rejected - that's a different case, each endpoint already handles "not enrolled" on its own terms
 */
@Component
public class DisabledUserWebFilter implements WebFilter {

    private final WirehoodUserRepository wirehoodUserRepository;

    public DisabledUserWebFilter(WirehoodUserRepository wirehoodUserRepository) {
        this.wirehoodUserRepository = wirehoodUserRepository;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (header == null) {
            return chain.filter(exchange);
        }

        UUID userId;
        try {
            userId = UUID.fromString(header);
        } catch (IllegalArgumentException ex) {
            return chain.filter(exchange); // malformed - not this filter's job, downstream handles it
        }

        return wirehoodUserRepository.findById(userId)
                .flatMap(user -> user.isEnabled() ? chain.filter(exchange) : forbidden(exchange))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = "{\"status\":403,\"message\":\"Account disabled\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
