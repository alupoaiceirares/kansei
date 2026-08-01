package org.kansei.auth.dto;

import java.util.UUID;

/**
 * Returned by /register and /login on success.
 * The client will store the token and send it as "Authorization: Bearer <token>" for every request to any service
 */
public record AuthResponse(
        String token,
        UUID userId,
        String username
) {
}