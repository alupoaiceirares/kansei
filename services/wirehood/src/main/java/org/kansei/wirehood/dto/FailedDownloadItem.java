package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.DownloadRequest;
import org.kansei.wirehood.model.Track;

import java.time.Instant;
import java.util.UUID;

public record FailedDownloadItem(
        UUID trackId,
        String title,
        String artist,
        String format,
        Instant requestedAt
) {
    public static FailedDownloadItem of(Track track, DownloadRequest request) {
        return new FailedDownloadItem(
                track.getId(),
                track.getTitle(),
                track.getArtist(),
                request.getFormat(),
                request.getRequestedAt()
        );
    }
}
