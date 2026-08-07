package org.kansei.shieldwall.dto;

import java.util.UUID;

/**
 * Deliberately minimal - never email/password/credentialsVersion/anything else
 * Only for the internal id-to-username batch lookup other services use to display a human-readable name instead of a raw user id
 */
public record UserSummaryResponse(UUID id, String username) {
}
