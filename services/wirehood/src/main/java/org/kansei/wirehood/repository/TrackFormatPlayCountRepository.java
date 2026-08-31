package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.TrackFormatPlayCount;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * No single @Id column on TrackFormatPlayCount, same bare-Repository/custom-@Query shape as TrackFormatFavoriteRepository
 */
public interface TrackFormatPlayCountRepository extends Repository<TrackFormatPlayCount, Void> {

    // One row per (user, format), play_count increments on repeat plays instead of inserting a new row each time
    @Query("""
            INSERT INTO track_format_play_counts (user_id, track_format_id, play_count, last_played_at)
            VALUES (:userId, :trackFormatId, 1, :playedAt)
            ON CONFLICT (user_id, track_format_id)
            DO UPDATE SET play_count = track_format_play_counts.play_count + 1, last_played_at = :playedAt
            """)
    Mono<Void> recordPlay(UUID userId, UUID trackFormatId, Instant playedAt);

    // Batch lookup for a set of formats, used to merge this user's play counts into TrackService.getDetail
    @Query("SELECT * FROM track_format_play_counts WHERE user_id = :userId AND track_format_id IN (:trackFormatIds)")
    Flux<TrackFormatPlayCount> findByUserIdAndTrackFormatIdIn(UUID userId, Collection<UUID> trackFormatIds);

    // Summed across every format the user has ever played, backs MusicProfile.totalPlays
    @Query("SELECT COALESCE(SUM(play_count), 0) FROM track_format_play_counts WHERE user_id = :userId")
    Mono<Long> sumPlaysForUser(UUID userId);
}
