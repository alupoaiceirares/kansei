package org.kansei.tailwind.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service-to-service calls to shieldwall's internal user endpoint, never routed through control-tower.
 * Display lookups fail soft, the existence check for the purge job throws so a failure is never read as "user gone".
 */
@Slf4j
@Component
public class ShieldwallUserClient {

    // Usernames change rarely, TTL expiry only, no push invalidation
    private static final Duration USERNAME_CACHE_TTL = Duration.ofMinutes(15);
    private static final String USERNAME_KEY_PREFIX = "tailwind:username:";
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;
    private final String internalServiceSecret;
    private final StringRedisTemplate redisTemplate;

    public ShieldwallUserClient(
            @Value("${shieldwall.internal-uri}") String shieldwallInternalUri,
            @Value("${internal.service-secret}") String internalServiceSecret,
            StringRedisTemplate redisTemplate
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CALL_TIMEOUT).build());
        requestFactory.setReadTimeout(CALL_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(shieldwallInternalUri).requestFactory(requestFactory).build();
        this.internalServiceSecret = internalServiceSecret;
        this.redisTemplate = redisTemplate;
    }

    // Read-through Redis cache in front of shieldwall, any failure on either side falls back to fewer results, never an exception
    public Map<UUID, String> resolveUsernames(Collection<UUID> userIds) {
        List<UUID> ids = userIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> result = new HashMap<>();
        List<UUID> misses = new ArrayList<>();
        List<String> cached = readCache(ids);
        for (int i = 0; i < ids.size(); i++) {
            String username = cached.get(i);
            if (username != null) {
                result.put(ids.get(i), username);
            } else {
                misses.add(ids.get(i));
            }
        }

        if (!misses.isEmpty()) {
            try {
                for (UserSummary user : fetch(misses)) {
                    result.put(user.id(), user.username());
                    writeCache(user);
                }
            } catch (RuntimeException ex) {
                log.warn("shieldwall username lookup failed, returning what the cache had: {}", ex.toString());
            }
        }
        return result;
    }

    // Throws on any failure, the caller must tell "shieldwall is down" apart from "these users no longer exist"
    public Set<UUID> findExistingUserIds(Collection<UUID> userIds) {
        return fetch(userIds).stream().map(UserSummary::id).collect(Collectors.toSet());
    }

    private List<UserSummary> fetch(Collection<UUID> userIds) {
        String idsParam = userIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<UserSummary> body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/internal/users").queryParam("ids", idsParam).build())
                .header("X-Internal-Secret", internalServiceSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (body == null) {
            throw new IllegalStateException("shieldwall returned an empty body");
        }
        return body;
    }

    private List<String> readCache(List<UUID> ids) {
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(ids.stream().map(id -> USERNAME_KEY_PREFIX + id).toList());
            if (values != null && values.size() == ids.size()) {
                return values;
            }
        } catch (RuntimeException ex) {
            log.warn("username cache read failed, treating every id as a miss: {}", ex.toString());
        }
        return Collections.nCopies(ids.size(), null);
    }

    private void writeCache(UserSummary user) {
        try {
            redisTemplate.opsForValue().set(USERNAME_KEY_PREFIX + user.id(), user.username(), USERNAME_CACHE_TTL);
        } catch (RuntimeException ex) {
            log.warn("username cache write failed: {}", ex.toString());
        }
    }

    // Friend-search typeahead, same fail-soft shape as resolveUsernames: shieldwall down means no matches, never an error
    public List<UserMatch> searchUsers(String query, int limit) {
        try {
            List<UserMatch> body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/internal/users/search")
                            .queryParam("query", query).queryParam("limit", limit).build())
                    .header("X-Internal-Secret", internalServiceSecret)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return body == null ? List.of() : body;
        } catch (RuntimeException ex) {
            log.warn("shieldwall user search failed: {}", ex.toString());
            return List.of();
        }
    }

    // Email addresses for a mail sent right now, never cached or stored. Throws on failure so the sender retries later
    public List<Contact> findContacts(Collection<UUID> userIds) {
        String idsParam = userIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<Contact> body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/internal/users/contacts").queryParam("ids", idsParam).build())
                .header("X-Internal-Secret", internalServiceSecret)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return body == null ? List.of() : body;
    }

    private record UserSummary(UUID id, String username) {
    }

    public record Contact(UUID id, String username, String email) {
    }

    public record UserMatch(UUID id, String username) {
    }
}
