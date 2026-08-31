package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.FavoriteResponse;
import org.kansei.wirehood.dto.PageResponse;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.TrackFormatFavorite;
import org.kansei.wirehood.repository.TrackFormatFavoriteRepository;
import org.kansei.wirehood.repository.TrackFormatRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-user favorite, scoped to a specific track_format (mp3 and mp4 of the same track favorited independently).
 * No readiness/library-membership gate, same looseness as GenreTagService, only the format needs to exist
 */
@Service
public class FavoriteService {

    private final TrackFormatFavoriteRepository trackFormatFavoriteRepository;
    private final TrackFormatRepository trackFormatRepository;
    private final TrackRepository trackRepository;

    public FavoriteService(
            TrackFormatFavoriteRepository trackFormatFavoriteRepository,
            TrackFormatRepository trackFormatRepository,
            TrackRepository trackRepository
    ) {
        this.trackFormatFavoriteRepository = trackFormatFavoriteRepository;
        this.trackFormatRepository = trackFormatRepository;
        this.trackRepository = trackRepository;
    }

    public Mono<Void> favorite(UUID userId, UUID trackFormatId) {
        return trackFormatRepository.existsById(trackFormatId)
                .flatMap(exists -> exists
                        ? trackFormatFavoriteRepository.upsertFavorite(userId, trackFormatId, Instant.now())
                        : Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Format not found")));
    }

    public Mono<Void> unfavorite(UUID userId, UUID trackFormatId) {
        return trackFormatFavoriteRepository.deleteFavorite(userId, trackFormatId);
    }

    // Batches format + track lookups across the page (2 queries), same shape as TrackService.getLibrary
    public Mono<PageResponse<FavoriteResponse>> list(UUID userId, int page, int size) {
        int clampedSize = clampSize(size);
        int clampedPage = Math.max(page, 0);

        return Mono.zip(
                        trackFormatFavoriteRepository.findByUserId(userId, clampedSize, (long) clampedPage * clampedSize).collectList(),
                        trackFormatFavoriteRepository.countByUserId(userId))
                .flatMap(counted -> {
                    List<TrackFormatFavorite> favorites = counted.getT1();
                    long total = counted.getT2();
                    if (favorites.isEmpty()) {
                        return Mono.just(PageResponse.of(List.<FavoriteResponse>of(), clampedPage, clampedSize, total));
                    }

                    List<UUID> formatIds = favorites.stream().map(TrackFormatFavorite::getTrackFormatId).collect(Collectors.toList());
                    return trackFormatRepository.findAllById(formatIds).collectMap(TrackFormat::getId)
                            .flatMap(formatsById -> {
                                List<UUID> trackIds = formatsById.values().stream()
                                        .map(TrackFormat::getTrackId).distinct().collect(Collectors.toList());
                                return trackRepository.findAllById(trackIds).collectMap(Track::getId)
                                        .map(tracksById -> toPage(favorites, formatsById, tracksById, clampedPage, clampedSize, total));
                            });
                });
    }

    private PageResponse<FavoriteResponse> toPage(
            List<TrackFormatFavorite> favorites,
            Map<UUID, TrackFormat> formatsById,
            Map<UUID, Track> tracksById,
            int page,
            int size,
            long total
    ) {
        List<FavoriteResponse> items = favorites.stream()
                .map(favorite -> {
                    TrackFormat format = formatsById.get(favorite.getTrackFormatId());
                    Track track = format == null ? null : tracksById.get(format.getTrackId());
                    return format == null || track == null ? null : FavoriteResponse.of(favorite, format, track);
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());
        return PageResponse.of(items, page, size, total);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
