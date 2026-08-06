package org.kansei.wirehood.youtube;

/**
 * Search/metadata only, per repo CLAUDE.md - no video stream is ever touched here
 */
public record YouTubeSearchResult(
        String videoId,
        String title,
        String channelTitle,
        String thumbnailUrl,
        long durationSeconds
) {
}
