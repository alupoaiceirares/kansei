package org.kansei.tailwind.dto;

import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.model.Visibility;

import java.time.Instant;
import java.util.UUID;

/**
 * Username is resolved live from shieldwall (cached), so it is null when shieldwall is unreachable.
 * Role comes straight from the X-User-Role header, tailwind has no role column.
 */
public record TailwindUserResponse(
        UUID userId,
        String username,
        Instant joinedAt,
        boolean enabled,
        Visibility defaultVisibility,
        String role
) {

    public static TailwindUserResponse of(TailwindUser user, String username, String role) {
        return new TailwindUserResponse(user.getUserId(), username, user.getJoinedAt(), user.isEnabled(), user.getDefaultVisibility(), role);
    }
}
