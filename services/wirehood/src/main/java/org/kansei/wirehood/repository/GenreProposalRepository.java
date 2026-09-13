package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.GenreProposal;
import org.kansei.wirehood.model.GenreProposalStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GenreProposalRepository extends ReactiveCrudRepository<GenreProposal, UUID> {

    // Global admin review queue
    Flux<GenreProposal> findByStatus(GenreProposalStatus status, Pageable pageable);

    Mono<Long> countByStatus(GenreProposalStatus status);

    // Dedup check on submit - case-insensitive so "Lo-Fi" and "lo-fi" aren't both proposed
    Mono<Boolean> existsByStatusAndNameIgnoreCase(GenreProposalStatus status, String name);
}
