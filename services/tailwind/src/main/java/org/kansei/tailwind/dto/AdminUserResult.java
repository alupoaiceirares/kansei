package org.kansei.tailwind.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * An opted-in user as the admin screens list them, disabled ones included. disabledAt is null while enabled.
 */
public record AdminUserResult(UUID userId, String username, boolean enabled, Instant joinedAt, Instant disabledAt) {
}
