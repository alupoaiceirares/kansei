package org.kansei.wirehood.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Metadata already known from the search step + whatever the user edited in the parse-and-confirm popup
 * Only used if the track doesn't already exist, an existing track's own columns win, this request's values are never applied on top of an existing row
 * Format is "mp3" or "mp4", dedup/queueing is keyed on (youtubeVideoId, format), not youtubeVideoId alone
 */
public record SubmitDownloadRequest(
        @NotBlank @Size(max = 20) String youtubeVideoId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String artist,
        @Size(max = 255) String extraInfo,
        @Min(0) long durationSeconds,
        @NotBlank @Pattern(regexp = "mp3|mp4") String format
) {
}
