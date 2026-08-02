package org.kansei.shieldwall.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String firstName,
        String lastName,
        boolean active,
        Instant createdAt
) {
}