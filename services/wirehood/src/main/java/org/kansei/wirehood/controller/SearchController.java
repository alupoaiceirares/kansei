package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.ExistingTrackResponse;
import org.kansei.wirehood.parser.ParsedTitle;
import org.kansei.wirehood.parser.TrackTitleParser;
import org.kansei.wirehood.service.TrackService;
import org.kansei.wirehood.youtube.YouTubeSearchResult;
import org.kansei.wirehood.youtube.YouTubeUrlExtractor;
import org.kansei.wirehood.youtube.YtDlpSearchClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping("/wirehood")
public class SearchController {

    private final YtDlpSearchClient searchClient;
    private final TrackService trackService;

    public SearchController(YtDlpSearchClient searchClient, TrackService trackService) {
        this.searchClient = searchClient;
        this.trackService = trackService;
    }

    /**
     * If `q` is a pasted YouTube link, resolves it directly (single result) instead of running it through ytsearch as literal query text, which would just search for the URL string itself and return nothing relevant
     */
    @GetMapping("/search")
    public Flux<YouTubeSearchResult> search(@RequestParam String q) {
        Optional<String> videoId = YouTubeUrlExtractor.extractVideoId(q);
        return videoId.isPresent()
                ? Flux.from(searchClient.lookupByVideoId(videoId.get()))
                : searchClient.search(q);
    }

    /**
     * Pure parsing, no YouTube call - just the deterministic guess for the confirm popup
     * The user's own edits (not this endpoint) are what actually get saved, at download-submit time
     */
    @GetMapping("/search/parse-title")
    public ParsedTitle parseTitle(@RequestParam String title) {
        return TrackTitleParser.parse(title);
    }

    // Checked right before the confirm popup opens - if this video's already on the platform, the
    // frontend greys out the title/artist/extra-info fields instead of letting the user edit values
    // that would just be silently discarded on submit (existing track's own columns always win)
    @GetMapping("/search/existing-track")
    public Mono<ExistingTrackResponse> existingTrack(@RequestParam String videoId) {
        return trackService.findExistingByVideoId(videoId);
    }
}
