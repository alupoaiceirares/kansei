package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.Track;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// thumbnailPath deliberately excluded - see TrackDetailResponse for the reasoning
public record LibraryTrackResponse(
        UUID trackId,
        String title,
        String artist,
        String extraInfo,
        Integer durationSeconds,
        boolean hasThumbnail,
        List<TrackFormatSummary> formats,
        Instant addedAt
) {
    public static LibraryTrackResponse of(Track track, List<TrackFormatSummary> formats, Instant addedAt) {
        return new LibraryTrackResponse(
                track.getId(),
                track.getTitle(),
                track.getArtist(),
                track.getExtraInfo(),
                track.getDurationSeconds(),
                track.getThumbnailPath() != null,
                formats,
                addedAt
        );
    }
}
