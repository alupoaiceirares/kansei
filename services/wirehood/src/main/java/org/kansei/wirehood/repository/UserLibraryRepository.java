package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.UserLibrary;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserLibraryRepository extends ReactiveCrudRepository<UserLibrary, UUID> {

    Mono<Boolean> existsByUserIdAndTrackId(UUID userId, UUID trackId);

    Flux<UserLibrary> findByUserId(UUID userId);
}
