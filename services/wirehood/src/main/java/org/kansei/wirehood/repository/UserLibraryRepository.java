package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.UserLibrary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserLibraryRepository extends ReactiveCrudRepository<UserLibrary, UUID> {

    Mono<Boolean> existsByUserIdAndTrackId(UUID userId, UUID trackId);

    // Unpaged, used by MusicProfileService/DataExportService, which need the whole library, not a page of it
    Flux<UserLibrary> findByUserId(UUID userId);

    // Paged, backs GET /wirehood/library specifically (TrackService.getLibrary)
    Flux<UserLibrary> findByUserId(UUID userId, Pageable pageable);

    Mono<Long> countByUserId(UUID userId);
}
