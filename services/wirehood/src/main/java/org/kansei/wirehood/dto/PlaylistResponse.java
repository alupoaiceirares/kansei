package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.Playlist;

import java.time.Instant;
import java.util.UUID;

// Summary view for listing ("my playlists") - no track/collaborator detail
public record PlaylistResponse(
        UUID id,
        UUID ownerId,
        String ownerUsername,
        String name,
        boolean shared,
        Instant createdAt,
        long trackCount
) {
    private static final String UNKNOWN_USERNAME = "Unknown user";

    public static PlaylistResponse from(Playlist playlist, String ownerUsername, long trackCount) {
        return new PlaylistResponse(
                playlist.getId(),
                playlist.getOwnerId(),
                ownerUsername == null ? UNKNOWN_USERNAME : ownerUsername,
                playlist.getName(),
                playlist.isShared(),
                playlist.getCreatedAt(),
                trackCount
        );
    }
}
