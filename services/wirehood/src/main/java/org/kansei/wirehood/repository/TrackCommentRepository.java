package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.TrackComment;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface TrackCommentRepository extends ReactiveCrudRepository<TrackComment, UUID> {

    Mono<Long> countByTrackId(UUID trackId);

    // Keyset pagination, this is more suited for a live comment section than offset pagination
    @Query("""
            SELECT * FROM track_comments
            WHERE track_id = :trackId
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            """)
    Flux<TrackComment> findFirstPage(UUID trackId, int limit);

    @Query("""
            SELECT * FROM track_comments
            WHERE track_id = :trackId
              AND (created_at, id) > (:afterCreatedAt, :afterId)
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            """)
    Flux<TrackComment> findAfterCursor(UUID trackId, Instant afterCreatedAt, UUID afterId, int limit);
}
