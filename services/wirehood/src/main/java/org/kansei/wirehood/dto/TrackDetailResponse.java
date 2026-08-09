package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.Track;

import java.util.List;
import java.util.UUID;

// thumbnailPath deliberately excluded - server-internal disk location, same reasoning as TrackFormatSummary
// excluding filePath. hasThumbnail tells the frontend whether GET .../thumbnail will return anything.
public record TrackDetailResponse(
        UUID id,
        String youtubeVideoId,
        String title,
        String artist,
        String extraInfo,
        Integer durationSeconds,
        boolean hasThumbnail,
        boolean visible,
        List<TrackFormatSummary> formats
) {
    public static TrackDetailResponse of(Track track, List<TrackFormatSummary> formats) {
        return new TrackDetailResponse(
                track.getId(),
                track.getYoutubeVideoId(),
                track.getTitle(),
                track.getArtist(),
                track.getExtraInfo(),
                track.getDurationSeconds(),
                track.getThumbnailPath() != null,
                track.isVisible(),
                formats
        );
    }
}
