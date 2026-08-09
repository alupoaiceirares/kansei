package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.PlaylistTrack;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * No single @Id column on PlaylistTrack (composite PK playlist_id+track_id), so this extends the bare Repository marker instead of ReactiveCrudRepository, every method here is a parameterized @Query, not a generated one
 */
public interface PlaylistTrackRepository extends Repository<PlaylistTrack, Void> {

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id = :playlistId")
    Mono<Integer> nextPosition(UUID playlistId);

    @Query("INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (:playlistId, :trackId, :position)")
    Mono<Void> insert(UUID playlistId, UUID trackId, int position);

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    Mono<Void> deleteByPlaylistIdAndTrackId(UUID playlistId, UUID trackId);

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId)")
    Mono<Boolean> existsByPlaylistIdAndTrackId(UUID playlistId, UUID trackId);

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position ASC")
    Flux<PlaylistTrack> findByPlaylistIdOrderByPosition(UUID playlistId);

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlist_id = :playlistId AND track_id = :trackId")
    Mono<Void> updatePosition(UUID playlistId, UUID trackId, int position);

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = :playlistId")
    Mono<Long> countByPlaylistId(UUID playlistId);
}
