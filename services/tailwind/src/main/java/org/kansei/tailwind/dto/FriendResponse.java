package org.kansei.tailwind.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * username is null when shieldwall could not be reached, the friendship itself is still valid.
 */
public record FriendResponse(UUID userId, String username, Instant friendsSince) {
}
