package org.kansei.shieldwall.dto;

import java.util.UUID;

/**
 * Internal only, the address another service mails at that moment (tailwind's yearly recap). Callers never store it.
 */
public record UserContactResponse(UUID id, String username, String email) {
}
