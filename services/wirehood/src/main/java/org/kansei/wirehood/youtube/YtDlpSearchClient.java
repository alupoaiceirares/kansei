package org.kansei.wirehood.youtube;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Search via `yt-dlp` instead of the official YouTube Data API, `ytsearch:` + `--flat-playlist` avoids per-video page fetches - one process call per search
 */
@Component
public class YtDlpSearchClient {

    private static final int MAX_RESULTS = 30;

    private final ObjectMapper objectMapper;

    public YtDlpSearchClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Flux<YouTubeSearchResult> search(String query) {
        return Mono.fromCallable(() -> runSearch(query))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Direct lookup for a pasted YouTube link, resolved by SearchController via
     * YouTubeUrlExtractor - not a search, hits the video's own page instead of ytsearch
     */
    public Mono<YouTubeSearchResult> lookupByVideoId(String videoId) {
        return Mono.fromCallable(() -> runLookup(videoId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private YouTubeSearchResult runLookup(String videoId) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "yt-dlp", "https://www.youtube.com/watch?v=" + videoId,
                "--dump-json", "--no-playlist", "--no-warnings"
        ).start();

        String json;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            json = reader.readLine();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || json == null || json.isBlank()) {
            throw new IllegalStateException("yt-dlp lookup exited with code " + exitCode + " for video: " + videoId);
        }

        return toResult(objectMapper.readValue(json, YtDlpVideoInfo.class));
    }

    private YouTubeSearchResult toResult(YtDlpVideoInfo info) {
        String channel = info.channel() != null ? info.channel() : info.uploader();
        long durationSeconds = info.duration() == null ? 0 : Math.round(info.duration());

        return new YouTubeSearchResult(info.id(), info.title(), channel, info.thumbnail(), durationSeconds);
    }

    private List<YouTubeSearchResult> runSearch(String query) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "yt-dlp", "ytsearch" + MAX_RESULTS + ":" + query,
                "--dump-json", "--flat-playlist", "--no-warnings"
        ).start();

        List<YouTubeSearchResult> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                results.add(toResult(objectMapper.readValue(line, YtDlpSearchEntry.class)));
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("yt-dlp search exited with code " + exitCode + " for query: " + query);
        }
        return results;
    }

    private YouTubeSearchResult toResult(YtDlpSearchEntry entry) {
        String channel = entry.channel() != null ? entry.channel() : entry.uploader();
        String thumbnailUrl = (entry.thumbnails() == null || entry.thumbnails().isEmpty())
                ? null
                : entry.thumbnails().get(entry.thumbnails().size() - 1).url();
        long durationSeconds = entry.duration() == null ? 0 : Math.round(entry.duration());

        return new YouTubeSearchResult(entry.id(), entry.title(), channel, thumbnailUrl, durationSeconds);
    }
}
