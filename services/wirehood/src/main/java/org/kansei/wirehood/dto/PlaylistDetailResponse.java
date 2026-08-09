package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.Playlist;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistDetailResponse(
        UUID id,
        UUID ownerId,
        String ownerUsername,
        String name,
        boolean shared,
        Instant createdAt,
        List<PlaylistTrackItem> tracks,
        List<CollaboratorResponse> collaborators
) {
    private static final String UNKNOWN_USERNAME = "Unknown user";

    public static PlaylistDetailResponse from(
            Playlist playlist,
            String ownerUsername,
            List<PlaylistTrackItem> tracks,
            List<CollaboratorResponse> collaborators
    ) {
        return new PlaylistDetailResponse(
                playlist.getId(),
                playlist.getOwnerId(),
                ownerUsername == null ? UNKNOWN_USERNAME : ownerUsername,
                playlist.getName(),
                playlist.isShared(),
                playlist.getCreatedAt(),
                tracks,
                collaborators
        );
    }
}
