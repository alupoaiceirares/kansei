package org.kansei.wirehood.controller;

import org.kansei.wirehood.parser.ParsedTitle;
import org.kansei.wirehood.parser.TrackTitleParser;
import org.kansei.wirehood.youtube.YouTubeSearchResult;
import org.kansei.wirehood.youtube.YtDlpSearchClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/wirehood")
public class SearchController {

    private final YtDlpSearchClient searchClient;

    public SearchController(YtDlpSearchClient searchClient) {
        this.searchClient = searchClient;
    }

    @GetMapping("/search")
    public Flux<YouTubeSearchResult> search(@RequestParam String q) {
        return searchClient.search(q);
    }

    /**
     * Pure parsing, no YouTube call - just the deterministic guess for the confirm popup
     * The user's own edits (not this endpoint) are what actually get saved, at download-submit time
     */
    @GetMapping("/search/parse-title")
    public ParsedTitle parseTitle(@RequestParam String title) {
        return TrackTitleParser.parse(title);
    }
}
