package org.kansei.tailwind.dto;

import java.util.UUID;

/**
 * A friend who logged the same flight and lets the viewer see it.
 */
public record SharedFlightFriend(UUID userId, String username) {
}
