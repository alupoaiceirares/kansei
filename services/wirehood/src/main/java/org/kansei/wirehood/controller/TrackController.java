package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.CommentResponse;
import org.kansei.wirehood.dto.CreateCommentRequest;
import org.kansei.wirehood.dto.GenreVoteResponse;
import org.kansei.wirehood.dto.TagTrackRequest;
import org.kansei.wirehood.dto.ThumbnailSubmissionResponse;
import org.kansei.wirehood.dto.TrackDetailResponse;
import org.kansei.wirehood.service.CommentService;
import org.kansei.wirehood.service.GenreTagService;
import org.kansei.wirehood.service.ThumbnailSubmissionService;
import org.kansei.wirehood.service.TrackService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/wirehood/tracks/{trackId}")
public class TrackController {

    private final GenreTagService genreTagService;
    private final CommentService commentService;
    private final TrackService trackService;
    private final ThumbnailSubmissionService thumbnailSubmissionService;

    public TrackController(
            GenreTagService genreTagService,
            CommentService commentService,
            TrackService trackService,
            ThumbnailSubmissionService thumbnailSubmissionService
    ) {
        this.genreTagService = genreTagService;
        this.commentService = commentService;
        this.trackService = trackService;
        this.thumbnailSubmissionService = thumbnailSubmissionService;
    }

    // Track metadata + every format that's been attempted for it (mp3, mp4, ...) - frontend's "what do we have for this track" view
    @GetMapping
    public Mono<TrackDetailResponse> detail(@PathVariable UUID trackId) {
        return trackService.getDetail(trackId);
    }

    // Streams the thumbnail bytes directly - never exposes the server-side file path
    @GetMapping("/thumbnail")
    public Mono<ResponseEntity<Resource>> thumbnail(@PathVariable UUID trackId) {
        return trackService.getThumbnail(trackId);
    }

    // Any wirehood user, submission sits PENDING until an admin approves/rejects it
    @PostMapping(value = "/thumbnail-submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ThumbnailSubmissionResponse>> submitThumbnail(
            @PathVariable UUID trackId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestPart("file") Mono<FilePart> filePart
    ) {
        return filePart.flatMap(part -> thumbnailSubmissionService.submit(trackId, userId, part))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    // Tag or re-tag a track with one or more genres - idempotent, one vote per (track, genre, user)
    @PostMapping("/genre-tags")
    public Mono<Void> tag(
            @PathVariable UUID trackId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody TagTrackRequest request
    ) {
        return genreTagService.tag(trackId, request.genreIds(), userId);
    }

    @GetMapping("/genre-tags")
    public Flux<GenreVoteResponse> tags(@PathVariable UUID trackId) {
        return genreTagService.tagsForTrack(trackId);
    }

    // parentCommentId in the body (null = top-level) makes this a reply if set
    @PostMapping("/comments")
    public Mono<CommentResponse> comment(
            @PathVariable UUID trackId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody CreateCommentRequest request
    ) {
        return commentService.post(trackId, userId, request);
    }

    // Flat list, oldest first - frontend builds the reply tree from each comment's parentCommentId
    @GetMapping("/comments")
    public Flux<CommentResponse> comments(@PathVariable UUID trackId) {
        return commentService.listForTrack(trackId);
    }
}
