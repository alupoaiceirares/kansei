package org.kansei.wirehood.dto;

import java.util.UUID;

public record FriendSearchResult(UUID userId, String username, FriendshipRelation relation) {
}
