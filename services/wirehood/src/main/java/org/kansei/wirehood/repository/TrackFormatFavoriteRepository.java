package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.TrackFormatFavorite;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * No single @Id column on TrackFormatFavorite, so this extends the bare Repository marker, every method is a custom @Query, same pattern as TrackGenreTagRepository
 */
public interface TrackFormatFavoriteRepository extends Repository<TrackFormatFavorite, Void> {

    @Query("INSERT INTO track_format_favorites (user_id, track_format_id, favorited_at) VALUES (:userId, :trackFormatId, :favoritedAt) ON CONFLICT (user_id, track_format_id) DO NOTHING")
    Mono<Void> upsertFavorite(UUID userId, UUID trackFormatId, Instant favoritedAt);

    @Query("DELETE FROM track_format_favorites WHERE user_id = :userId AND track_format_id = :trackFormatId")
    Mono<Void> deleteFavorite(UUID userId, UUID trackFormatId);

    // Batch membership check for a set of formats (e.g. every format on a track), used to flag "favorited" in TrackService.getDetail
    @Query("SELECT * FROM track_format_favorites WHERE user_id = :userId AND track_format_id IN (:trackFormatIds)")
    Flux<TrackFormatFavorite> findByUserIdAndTrackFormatIdIn(UUID userId, Collection<UUID> trackFormatIds);

    @Query("SELECT * FROM track_format_favorites WHERE user_id = :userId ORDER BY favorited_at DESC LIMIT :limit OFFSET :offset")
    Flux<TrackFormatFavorite> findByUserId(UUID userId, int limit, long offset);

    @Query("SELECT COUNT(*) FROM track_format_favorites WHERE user_id = :userId")
    Mono<Long> countByUserId(UUID userId);
}
