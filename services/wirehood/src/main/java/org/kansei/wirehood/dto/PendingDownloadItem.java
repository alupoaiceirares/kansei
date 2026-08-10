package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.DownloadRequest;
import org.kansei.wirehood.model.Track;

import java.time.Instant;
import java.util.UUID;

public record PendingDownloadItem(
        UUID trackId,
        String title,
        String artist,
        String format,
        Instant requestedAt
) {
    public static PendingDownloadItem of(Track track, DownloadRequest request) {
        return new PendingDownloadItem(
                track.getId(),
                track.getTitle(),
                track.getArtist(),
                request.getFormat(),
                request.getRequestedAt()
        );
    }
}
