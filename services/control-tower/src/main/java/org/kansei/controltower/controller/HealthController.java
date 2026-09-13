package org.kansei.controltower.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Lets soundwave show a "service is down" overlay instead of a generic broken page when wirehood
 * itself is unreachable - distinct from control-tower's own /actuator/health, which only reflects
 * control-tower's own health (e.g. its Redis connection), not whether it can actually reach the
 * backend it proxies to. Public (see JwtAuthenticationFilter.PUBLIC_PATHS) since a logged-out or
 * expired-token visitor should still be able to see "wirehood is down", not get a 401 instead.
 */
@RestController
public class HealthController {

    private final WebClient webClient;

    public HealthController(WebClient.Builder webClientBuilder, @Value("${WIREHOOD_URI}") String wirehoodUri) {
        this.webClient = webClientBuilder.baseUrl(wirehoodUri).build();
    }

    // Liveness specifically, not the full aggregate /actuator/health - that one factors in every
    // auto-detected sub-indicator (DB, Redis, disk...), so a struggling dependency (e.g. Redis
    // down) would report wirehood itself as "down" even though it's actually still up and serving
    // everything that doesn't touch that dependency. Liveness only reflects whether the app
    // process itself is broken/deadlocked, which is what "should the overlay show" actually means.
    @GetMapping("/health/wirehood")
    public Mono<Map<String, String>> wirehoodHealth() {
        return webClient.get()
                .uri("/actuator/health/liveness")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(2))
                .map(response -> Map.of("status", "UP"))
                .onErrorReturn(Map.of("status", "DOWN"));
    }
}
