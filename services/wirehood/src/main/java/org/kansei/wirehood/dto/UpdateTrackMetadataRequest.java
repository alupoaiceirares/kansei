package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Full replace, admin fixing a bad parse-and-confirm entry, artist/extraInfo are nullable columns, sending null clears them
public record UpdateTrackMetadataRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String artist,
        @Size(max = 255) String extraInfo
) {
}
