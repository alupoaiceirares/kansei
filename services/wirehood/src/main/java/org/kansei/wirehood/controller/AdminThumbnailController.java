package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.PageResponse;
import org.kansei.wirehood.dto.ThumbnailSubmissionResponse;
import org.kansei.wirehood.model.ThumbnailSubmissionStatus;
import org.kansei.wirehood.service.ThumbnailSubmissionService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

// Global queue across every track, not per-track, one place for an admin to review everything
@RestController
@RequestMapping("/wirehood/admin/thumbnail-submissions")
public class AdminThumbnailController {

    private final ThumbnailSubmissionService thumbnailSubmissionService;

    public AdminThumbnailController(ThumbnailSubmissionService thumbnailSubmissionService) {
        this.thumbnailSubmissionService = thumbnailSubmissionService;
    }

    @GetMapping
    public Mono<PageResponse<ThumbnailSubmissionResponse>> list(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false, defaultValue = "PENDING") ThumbnailSubmissionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return thumbnailSubmissionService.listByStatus(userId, status, page, size);
    }

    // Lets an admin actually see the submitted image before deciding
    @GetMapping("/{submissionId}/file")
    public Mono<ResponseEntity<Resource>> file(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID submissionId) {
        return thumbnailSubmissionService.getFile(userId, submissionId);
    }

    @PostMapping("/{submissionId}/approve")
    public Mono<Void> approve(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID submissionId) {
        return thumbnailSubmissionService.approve(submissionId, userId);
    }

    @PostMapping("/{submissionId}/reject")
    public Mono<Void> reject(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID submissionId) {
        return thumbnailSubmissionService.reject(submissionId, userId);
    }
}
