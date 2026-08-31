package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.TrackFormatFavorite;

import java.time.Instant;
import java.util.UUID;

public record FavoriteResponse(
        UUID trackFormatId,
        UUID trackId,
        String title,
        String artist,
        String extraInfo,
        String format,
        String quality,
        Instant favoritedAt
) {
    public static FavoriteResponse of(TrackFormatFavorite favorite, TrackFormat trackFormat, Track track) {
        return new FavoriteResponse(
                favorite.getTrackFormatId(),
                track.getId(),
                track.getTitle(),
                track.getArtist(),
                track.getExtraInfo(),
                trackFormat.getFormat(),
                trackFormat.getQuality(),
                favorite.getFavoritedAt()
        );
    }
}
