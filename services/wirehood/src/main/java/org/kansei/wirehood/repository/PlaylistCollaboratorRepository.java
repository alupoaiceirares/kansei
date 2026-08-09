package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.PlaylistCollaborator;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * No single @Id column on PlaylistCollaborator (composite PK playlist_id+user_id), same pattern as PlaylistTrackRepository
 */
public interface PlaylistCollaboratorRepository extends Repository<PlaylistCollaborator, Void> {

    // Idempotent - a repeat add is a harmless no-op, not a constraint-violation error
    @Query("INSERT INTO playlist_collaborators (playlist_id, user_id, added_at) VALUES (:playlistId, :userId, :addedAt) ON CONFLICT (playlist_id, user_id) DO NOTHING")
    Mono<Void> upsert(UUID playlistId, UUID userId, Instant addedAt);

    @Query("DELETE FROM playlist_collaborators WHERE playlist_id = :playlistId AND user_id = :userId")
    Mono<Void> deleteByPlaylistIdAndUserId(UUID playlistId, UUID userId);

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_collaborators WHERE playlist_id = :playlistId AND user_id = :userId)")
    Mono<Boolean> existsByPlaylistIdAndUserId(UUID playlistId, UUID userId);

    @Query("SELECT * FROM playlist_collaborators WHERE playlist_id = :playlistId")
    Flux<PlaylistCollaborator> findByPlaylistId(UUID playlistId);
}
