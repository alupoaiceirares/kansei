package org.kansei.wirehood.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendResponse(UUID userId, String username, Instant since) {
    private static final String UNKNOWN_USERNAME = "Unknown user";

    public static FriendResponse of(UUID userId, Instant since, String username) {
        return new FriendResponse(userId, username == null ? UNKNOWN_USERNAME : username, since);
    }
}
