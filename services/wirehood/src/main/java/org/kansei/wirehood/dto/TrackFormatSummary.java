package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.TrackFormatStatus;

import java.util.UUID;

// filePath deliberately excluded - server-internal disk location, not something to leak to the frontend
public record TrackFormatSummary(UUID id, String format, String quality, TrackFormatStatus status, Long fileSizeBytes) {
    public static TrackFormatSummary from(TrackFormat format) {
        return new TrackFormatSummary(format.getId(), format.getFormat(), format.getQuality(), format.getStatus(), format.getFileSizeBytes());
    }
}
