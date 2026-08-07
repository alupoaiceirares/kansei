package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.TrackGenreTag;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * No single @Id column on TrackGenreTag, so this extends the bare Repository marker instead of ReactiveCrudRepository - every method here is a custom @Query, not a generated one
 */
public interface TrackGenreTagRepository extends Repository<TrackGenreTag, Void> {

    // One vote per (track, genre, user) - the PK enforces it, ON CONFLICT DO NOTHING makes a repeat tag a harmless no-op instead of a constraint-violation error
    @Query("INSERT INTO track_genre_tags (track_id, genre_id, user_id, tagged_at) VALUES (:trackId, :genreId, :userId, :taggedAt) ON CONFLICT (track_id, genre_id, user_id) DO NOTHING")
    Mono<Void> upsertTag(UUID trackId, UUID genreId, UUID userId, Instant taggedAt);

    @Query("SELECT * FROM track_genre_tags WHERE track_id = :trackId")
    Flux<TrackGenreTag> findByTrackId(UUID trackId);
}
