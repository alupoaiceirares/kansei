package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.GenreProposalResponse;
import org.kansei.wirehood.dto.PageResponse;
import org.kansei.wirehood.model.Genre;
import org.kansei.wirehood.model.GenreProposal;
import org.kansei.wirehood.model.GenreProposalStatus;
import org.kansei.wirehood.repository.GenreProposalRepository;
import org.kansei.wirehood.repository.GenreRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Crowd-proposed new genres, admin-approved before landing in the real (fixed, seeded) genres
 * table - same submit/approve/reject shape as ThumbnailSubmissionService, no file handling needed
 */
@Service
public class GenreProposalService {

    private final GenreProposalRepository genreProposalRepository;
    private final GenreRepository genreRepository;
    private final AdminAuthService adminAuthService;

    public GenreProposalService(
            GenreProposalRepository genreProposalRepository,
            GenreRepository genreRepository,
            AdminAuthService adminAuthService
    ) {
        this.genreProposalRepository = genreProposalRepository;
        this.genreRepository = genreRepository;
        this.adminAuthService = adminAuthService;
    }

    // Any wirehood user - rejects up front if the name already exists as a real genre or as
    // another still-pending proposal, rather than letting a queue fill up with duplicates an
    // admin would just reject one by one
    public Mono<GenreProposalResponse> submit(UUID userId, String rawName) {
        String name = rawName.trim();
        return genreRepository.existsByNameIgnoreCase(name)
                .flatMap(existsAsGenre -> existsAsGenre
                        ? Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "That genre already exists"))
                        : genreProposalRepository.existsByStatusAndNameIgnoreCase(GenreProposalStatus.PENDING, name))
                .flatMap(existsAsPending -> existsAsPending
                        ? Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Already proposed, pending review"))
                        : genreProposalRepository.save(GenreProposal.builder()
                                .name(name)
                                .submittedBy(userId)
                                .status(GenreProposalStatus.PENDING)
                                .submittedAt(Instant.now())
                                .build()))
                .map(GenreProposalResponse::from);
    }

    public Mono<PageResponse<GenreProposalResponse>> listByStatus(UUID adminUserId, GenreProposalStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.ASC, "submittedAt"));
        return adminAuthService.requireAdmin(adminUserId)
                .then(Mono.zip(
                        genreProposalRepository.findByStatus(status, pageable).map(GenreProposalResponse::from).collectList(),
                        genreProposalRepository.countByStatus(status)))
                .map(resolved -> PageResponse.of(resolved.getT1(), pageable.getPageNumber(), pageable.getPageSize(), resolved.getT2()));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    // Inserts the real Genre row, unless the same name got approved from a different proposal
    // in the meantime (re-checked here, not just at submit time) - then just marks this one
    // approved too without creating a duplicate genre
    public Mono<Void> approve(UUID proposalId, UUID adminUserId) {
        return adminAuthService.requireAdmin(adminUserId)
                .then(findPendingOr409(proposalId))
                .flatMap(proposal -> genreRepository.existsByNameIgnoreCase(proposal.getName())
                        .flatMap(alreadyExists -> alreadyExists ? Mono.empty() : genreRepository.save(Genre.builder().name(proposal.getName()).build()))
                        .then(markReviewed(proposal, GenreProposalStatus.APPROVED, adminUserId)));
    }

    public Mono<Void> reject(UUID proposalId, UUID adminUserId) {
        return adminAuthService.requireAdmin(adminUserId)
                .then(findPendingOr409(proposalId))
                .flatMap(proposal -> markReviewed(proposal, GenreProposalStatus.REJECTED, adminUserId));
    }

    private Mono<Void> markReviewed(GenreProposal proposal, GenreProposalStatus status, UUID adminUserId) {
        proposal.setStatus(status);
        proposal.setReviewedAt(Instant.now());
        proposal.setReviewedBy(adminUserId);
        return genreProposalRepository.save(proposal).then();
    }

    private Mono<GenreProposal> findPendingOr409(UUID proposalId) {
        return genreProposalRepository.findById(proposalId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Genre proposal not found")))
                .flatMap(proposal -> proposal.getStatus() == GenreProposalStatus.PENDING
                        ? Mono.just(proposal)
                        : Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Proposal already reviewed")));
    }
}
