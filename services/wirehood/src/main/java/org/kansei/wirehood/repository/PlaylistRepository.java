package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.Playlist;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface PlaylistRepository extends ReactiveCrudRepository<Playlist, UUID> {

    // "My playlists" = owned by me OR I'm a collaborator on it
    @Query("""
            SELECT DISTINCT p.* FROM playlists p
            LEFT JOIN playlist_collaborators pc ON pc.playlist_id = p.id
            WHERE p.owner_id = :userId OR pc.user_id = :userId
            ORDER BY p.created_at DESC
            """)
    Flux<Playlist> findAccessibleByUserId(UUID userId);

    // Owned only, not collaborated-on - used by data export ("your own musical data")
    Flux<Playlist> findByOwnerId(UUID ownerId);
}
