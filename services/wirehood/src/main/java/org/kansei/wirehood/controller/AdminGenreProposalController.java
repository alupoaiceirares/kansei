package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.GenreProposalResponse;
import org.kansei.wirehood.dto.PageResponse;
import org.kansei.wirehood.model.GenreProposalStatus;
import org.kansei.wirehood.service.GenreProposalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

// Global queue across every proposal, same shape as AdminThumbnailController
@RestController
@RequestMapping("/wirehood/admin/genre-proposals")
public class AdminGenreProposalController {

    private final GenreProposalService genreProposalService;

    public AdminGenreProposalController(GenreProposalService genreProposalService) {
        this.genreProposalService = genreProposalService;
    }

    @GetMapping
    public Mono<PageResponse<GenreProposalResponse>> list(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false, defaultValue = "PENDING") GenreProposalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return genreProposalService.listByStatus(userId, role, status, page, size);
    }

    @PostMapping("/{proposalId}/approve")
    public Mono<Void> approve(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID proposalId
    ) {
        return genreProposalService.approve(proposalId, userId, role);
    }

    @PostMapping("/{proposalId}/reject")
    public Mono<Void> reject(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID proposalId
    ) {
        return genreProposalService.reject(proposalId, userId, role);
    }
}
