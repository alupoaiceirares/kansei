package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.ThumbnailSubmissionStatus;
import org.kansei.wirehood.model.TrackThumbnailSubmission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TrackThumbnailSubmissionRepository extends ReactiveCrudRepository<TrackThumbnailSubmission, UUID> {

    // Global admin review queue
    Flux<TrackThumbnailSubmission> findByStatus(ThumbnailSubmissionStatus status, Pageable pageable);

    Mono<Long> countByStatus(ThumbnailSubmissionStatus status);

    // Used on approve to auto-reject every other still-pending submission for the same track
    Flux<TrackThumbnailSubmission> findByTrackIdAndStatus(UUID trackId, ThumbnailSubmissionStatus status);

    // Every status, used on track hard-delete to clean up leftover submission files (see TrackService.hardDelete)
    Flux<TrackThumbnailSubmission> findByTrackId(UUID trackId);
}
