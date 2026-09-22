package org.kansei.tailwind.service;

import org.kansei.tailwind.dto.UiPreferencesResponse;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Display choices that follow the account instead of one browser. The stored value is opaque to tailwind,
 * it is only checked for being a JSON object within a size cap so it cannot be used as free storage.
 */
@Service
public class UiPreferencesService {

    private static final int MAX_JSON_LENGTH = 4000;
    private static final int MAX_KEYS = 40;

    private final TailwindUserRepository tailwindUserRepository;
    private final OptInGuard optInGuard;
    private final ObjectMapper objectMapper;

    public UiPreferencesService(TailwindUserRepository tailwindUserRepository, OptInGuard optInGuard, ObjectMapper objectMapper) {
        this.tailwindUserRepository = tailwindUserRepository;
        this.optInGuard = optInGuard;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UiPreferencesResponse get(UUID userId) {
        TailwindUser user = optInGuard.require(userId);
        return new UiPreferencesResponse(read(user.getUiPreferences()));
    }

    @Transactional
    public UiPreferencesResponse save(UUID userId, Map<String, Object> preferences) {
        TailwindUser user = optInGuard.require(userId);
        if (preferences.size() > MAX_KEYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many preference keys");
        }
        String json = objectMapper.writeValueAsString(preferences);
        if (json.length() > MAX_JSON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preferences are too large");
        }
        user.setUiPreferences(json);
        tailwindUserRepository.save(user);
        return new UiPreferencesResponse(preferences);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JacksonException ex) {
            // A row written by an older shape should not break the page, the frontend falls back to defaults
            return Map.of();
        }
    }
}
