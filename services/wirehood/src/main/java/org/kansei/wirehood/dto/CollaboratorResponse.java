package org.kansei.wirehood.dto;

import java.time.Instant;
import java.util.UUID;

public record CollaboratorResponse(UUID userId, String username, Instant addedAt) {
    private static final String UNKNOWN_USERNAME = "Unknown user";

    public static CollaboratorResponse from(UUID userId, Instant addedAt, String username) {
        return new CollaboratorResponse(userId, username == null ? UNKNOWN_USERNAME : username, addedAt);
    }
}
