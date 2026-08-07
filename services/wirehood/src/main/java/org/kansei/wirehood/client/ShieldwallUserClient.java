package org.kansei.wirehood.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Batch id-to-username lookup against shieldwall's internal endpoint - one call per list, not one per user, so viewing N comments doesn't mean N calls to shieldwall
 * Service-to-service only. On any failure (shieldwall down, network blip) falls back to an empty map rather than failing the caller's whole request
 */
@Component
public class ShieldwallUserClient {

    private final WebClient webClient;
    private final String internalServiceSecret;

    public ShieldwallUserClient(
            WebClient.Builder webClientBuilder,
            @Value("${shieldwall.internal-uri}") String shieldwallInternalUri,
            @Value("${internal.service-secret}") String internalServiceSecret
    ) {
        this.webClient = webClientBuilder.baseUrl(shieldwallInternalUri).build();
        this.internalServiceSecret = internalServiceSecret;
    }

    public Mono<Map<UUID, String>> resolveUsernames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        String idsParam = userIds.stream().map(UUID::toString).distinct().collect(Collectors.joining(","));

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/internal/users").queryParam("ids", idsParam).build())
                .header("X-Internal-Secret", internalServiceSecret)
                .retrieve()
                .bodyToFlux(UserSummary.class)
                .collectMap(UserSummary::id, UserSummary::username)
                .onErrorResume(ex -> Mono.just(Map.of()));
    }

    private record UserSummary(UUID id, String username) {
    }
}
