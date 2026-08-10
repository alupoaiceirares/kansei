package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.LibraryTrackResponse;
import org.kansei.wirehood.dto.PageResponse;
import org.kansei.wirehood.service.TrackService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/wirehood/library")
public class LibraryController {

    private final TrackService trackService;

    public LibraryController(TrackService trackService) {
        this.trackService = trackService;
    }

    @GetMapping
    public Mono<PageResponse<LibraryTrackResponse>> myLibrary(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return trackService.getLibrary(userId, page, size);
    }

    // Serves the actual file bytes for a format of a track the user has saved, browser handles the "save as" via Content-Disposition
    @GetMapping("/{trackId}/download")
    public Mono<ResponseEntity<Resource>> download(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID trackId,
            @RequestParam String format
    ) {
        return trackService.downloadFile(userId, trackId, format);
    }
}
