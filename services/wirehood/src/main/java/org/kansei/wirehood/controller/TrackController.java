package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.TagTrackRequest;
import org.kansei.wirehood.model.TrackGenreTag;
import org.kansei.wirehood.service.GenreTagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/wirehood/tracks/{trackId}/genre-tags")
public class TrackController {

    private final GenreTagService genreTagService;

    public TrackController(GenreTagService genreTagService) {
        this.genreTagService = genreTagService;
    }

    // Tag or re-tag a track with one or more genres - idempotent, one vote per (track, genre, user)
    @PostMapping
    public Mono<Void> tag(
            @PathVariable UUID trackId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody TagTrackRequest request
    ) {
        return genreTagService.tag(trackId, request.genreIds(), userId);
    }

    @GetMapping
    public Flux<TrackGenreTag> tags(@PathVariable UUID trackId) {
        return genreTagService.tagsForTrack(trackId);
    }
}
