package org.kansei.wirehood.repository;

import org.kansei.wirehood.dto.GenreVoteResponse;
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

    // Aggregate only, no per-user attribution - crowd-tagging is a vote count, not "who tagged what"
    @Query("""
            SELECT g.id AS genre_id, g.name AS genre_name, COUNT(*) AS votes
            FROM track_genre_tags t
            JOIN genres g ON g.id = t.genre_id
            WHERE t.track_id = :trackId
            GROUP BY g.id, g.name
            ORDER BY votes DESC
            """)
    Flux<GenreVoteResponse> countVotesByTrackId(UUID trackId);
}
