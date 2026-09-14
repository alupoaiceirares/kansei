package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.WirehoodUser;

import java.time.Instant;
import java.util.UUID;

// Same shape /wirehood/users/me and the opt-in response always returned, but role now comes from
// X-User-Role (control-tower's verified claim) instead of the wirehood_users.role column, which was dropped
public record WirehoodUserResponse(
        UUID userId,
        String role,
        Instant joinedAt,
        boolean enabled
) {
    private static final String DEFAULT_ROLE = "USER";

    public static WirehoodUserResponse of(WirehoodUser user, String role) {
        return new WirehoodUserResponse(
                user.getUserId(),
                role != null ? role : DEFAULT_ROLE,
                user.getJoinedAt(),
                user.isEnabled()
        );
    }
}
