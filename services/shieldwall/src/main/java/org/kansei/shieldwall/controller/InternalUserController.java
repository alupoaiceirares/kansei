package org.kansei.shieldwall.controller;

import org.kansei.shieldwall.dto.UserSummaryResponse;
import org.kansei.shieldwall.exception.InvalidInternalSecretException;
import org.kansei.shieldwall.model.User;
import org.kansei.shieldwall.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Service-to-service only - never routed through control-tower (no route declared for /api/internal/**), and even if hit directly the X-Internal-Secret header is required
 * Lets other services (wirehood, etc.) resolve a display username for a user id without caching user data themselves or exposing more than id+username
 */
@RestController
@RequestMapping("/api/internal")
public class InternalUserController {

    // Keeps a misconfigured/malicious caller from forcing one giant IN (...) query
    private static final int MAX_IDS = 200;

    // Search results stay small on purpose, this backs a friend-search typeahead, not a directory browse
    private static final int MAX_SEARCH_LIMIT = 50;
    private static final int DEFAULT_SEARCH_LIMIT = 20;

    private final UserRepository userRepository;
    private final String internalServiceSecret;

    public InternalUserController(
            UserRepository userRepository,
            @Value("${internal.service-secret}") String internalServiceSecret
    ) {
        this.userRepository = userRepository;
        this.internalServiceSecret = internalServiceSecret;
    }

    @GetMapping("/users")
    public List<UserSummaryResponse> findUsernames(
            @RequestHeader("X-Internal-Secret") String providedSecret,
            @RequestParam String ids
    ) {
        requireValidSecret(providedSecret);

        List<UUID> userIds = parseIds(ids);

        return userRepository.findAllById(userIds).stream()
                .map(user -> new UserSummaryResponse(user.getId(), user.getUsername()))
                .toList();
    }

    // ILIKE-prefix-style match (ContainingIgnoreCase), bounded result count - not a bulk "list all users" dump,
    // deliberately narrower than that so a leaked internal secret exposes at most one bounded page of matches
    @GetMapping("/users/search")
    public List<UserSummaryResponse> searchUsers(
            @RequestHeader("X-Internal-Secret") String providedSecret,
            @RequestParam String query,
            @RequestParam(required = false) Integer limit
    ) {
        requireValidSecret(providedSecret);

        if (query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }
        int pageSize = (limit == null) ? DEFAULT_SEARCH_LIMIT : Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);

        return userRepository.findByActiveTrueAndUsernameContainingIgnoreCase(query, PageRequest.of(0, pageSize)).stream()
                .map(user -> new UserSummaryResponse(user.getId(), user.getUsername()))
                .toList();
    }

    private void requireValidSecret(String providedSecret) {
        if (!internalServiceSecret.equals(providedSecret)) {
            throw new InvalidInternalSecretException();
        }
    }

    private List<UUID> parseIds(String ids) {
        List<String> rawIds = List.of(ids.split(","));
        if (rawIds.isEmpty() || rawIds.size() > MAX_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids must be 1-" + MAX_IDS + " comma-separated UUIDs");
        }
        try {
            return rawIds.stream().map(UUID::fromString).toList();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids contains a malformed UUID");
        }
    }
}
