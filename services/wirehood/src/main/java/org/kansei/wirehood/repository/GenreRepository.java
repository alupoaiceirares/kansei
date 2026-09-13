package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.Genre;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GenreRepository extends ReactiveCrudRepository<Genre, UUID> {

    // Dedup check when approving a proposal - case-insensitive, same reasoning as GenreProposalRepository's
    Mono<Boolean> existsByNameIgnoreCase(String name);
}
