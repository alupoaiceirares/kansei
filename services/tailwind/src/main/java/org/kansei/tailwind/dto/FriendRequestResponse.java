package org.kansei.tailwind.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending request, either one the caller received (INCOMING) or one they sent (OUTGOING).
 */
public record FriendRequestResponse(UUID userId, String username, Direction direction, Instant requestedAt) {

    public enum Direction {
        INCOMING,
        OUTGOING
    }
}
