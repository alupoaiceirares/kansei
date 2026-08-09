package org.kansei.wirehood.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestResponse(UUID userId, String username, FriendRequestDirection direction, Instant requestedAt) {
    private static final String UNKNOWN_USERNAME = "Unknown user";

    public static FriendRequestResponse of(UUID userId, FriendRequestDirection direction, Instant requestedAt, String username) {
        return new FriendRequestResponse(userId, username == null ? UNKNOWN_USERNAME : username, direction, requestedAt);
    }
}
