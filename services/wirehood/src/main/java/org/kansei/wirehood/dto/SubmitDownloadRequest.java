package org.kansei.wirehood.dto;

/**
 * Metadata already known from the search step + whatever the user edited in the parse-and-confirm
 * popup. Only used if the track doesn't already exist - an existing track's own columns win,
 * this request's values are never applied on top of an existing row.
 * format is "mp3" or "mp4" - dedup/queueing is keyed on (youtubeVideoId, format), not youtubeVideoId alone.
 */
public record SubmitDownloadRequest(
        String youtubeVideoId,
        String title,
        String artist,
        String extraInfo,
        long durationSeconds,
        String format
) {
}
