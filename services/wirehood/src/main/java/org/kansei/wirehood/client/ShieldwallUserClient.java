package org.kansei.wirehood.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Batch id-to-username lookup, one call per list, not one per user, on any failure falls back to an empty map rather than failing the caller's whole request
 */
@Component
public class ShieldwallUserClient {

    // Usernames change rarely - 10-15min staleness is a non-issue, TTL expiry only, no push-invalidation
    private static final Duration USERNAME_CACHE_TTL = Duration.ofMinutes(15);
    private static final String USERNAME_KEY_PREFIX = "wirehood:username:";

    private final WebClient webClient;
    private final String internalServiceSecret;
    private final ReactiveStringRedisTemplate redisTemplate;

    public ShieldwallUserClient(
            WebClient.Builder webClientBuilder,
            @Value("${shieldwall.internal-uri}") String shieldwallInternalUri,
            @Value("${internal.service-secret}") String internalServiceSecret,
            ReactiveStringRedisTemplate redisTemplate
    ) {
        this.webClient = webClientBuilder.baseUrl(shieldwallInternalUri).build();
        this.internalServiceSecret = internalServiceSecret;
        this.redisTemplate = redisTemplate;
    }

    // Read-through Redis cache in front of the shieldwall batch call below - only for display resolution, not searchUsers (a live typeahead's staleness is more visible, and search rarely repeats the same query)
    public Mono<Map<UUID, String>> resolveUsernames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        List<UUID> ids = userIds.stream().distinct().collect(Collectors.toList());
        List<String> keys = ids.stream().map(id -> USERNAME_KEY_PREFIX + id).collect(Collectors.toList());

        return redisTemplate.opsForValue().multiGet(keys)
                // Redis itself unreachable - treat every id as a cache miss and fall through to shieldwall, never fail the caller
                .onErrorReturn(Collections.nCopies(keys.size(), null))
                .flatMap(cached -> {
                    Map<UUID, String> hits = new HashMap<>();
                    List<UUID> misses = new ArrayList<>();
                    for (int i = 0; i < ids.size(); i++) {
                        String cachedUsername = cached.get(i);
                        if (cachedUsername != null) {
                            hits.put(ids.get(i), cachedUsername);
                        } else {
                            misses.add(ids.get(i));
                        }
                    }

                    if (misses.isEmpty()) {
                        return Mono.just(hits);
                    }
                    return fetchFromShieldwall(misses)
                            .flatMap(fetched -> cacheResults(fetched).thenReturn(fetched))
                            .map(fetched -> {
                                hits.putAll(fetched);
                                return hits;
                            });
                });
    }

    private Mono<Map<UUID, String>> fetchFromShieldwall(Collection<UUID> userIds) {
        String idsParam = userIds.stream().map(UUID::toString).distinct().collect(Collectors.joining(","));

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/internal/users").queryParam("ids", idsParam).build())
                .header("X-Internal-Secret", internalServiceSecret)
                .retrieve()
                .bodyToFlux(UserSummary.class)
                .collectMap(UserSummary::id, UserSummary::username)
                .onErrorResume(ex -> Mono.just(Map.of()));
    }

    // concatMap, not flatMap, same defensive habit as GenreTagService's batched writes, cheap insurance even though the documented bug there was R2DBC/Postgres-specific, not Redis
    private Mono<Void> cacheResults(Map<UUID, String> fetched) {
        if (fetched.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(fetched.entrySet())
                .concatMap(entry -> redisTemplate.opsForValue()
                        .set(USERNAME_KEY_PREFIX + entry.getKey(), entry.getValue(), USERNAME_CACHE_TTL))
                .onErrorResume(ex -> Mono.just(true))
                .then();
    }

    // Friend-search typeahead - same fail-soft shape as resolveUsernames (shieldwall down/slow -> empty list, never blocks the caller)
    public Mono<List<UserMatch>> searchUsers(String query, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/internal/users/search")
                        .queryParam("query", query)
                        .queryParam("limit", limit)
                        .build())
                .header("X-Internal-Secret", internalServiceSecret)
                .retrieve()
                .bodyToFlux(UserMatch.class)
                .collectList()
                .onErrorResume(ex -> Mono.just(List.of()));
    }

    private record UserSummary(UUID id, String username) {
    }

    public record UserMatch(UUID id, String username) {
    }
}
