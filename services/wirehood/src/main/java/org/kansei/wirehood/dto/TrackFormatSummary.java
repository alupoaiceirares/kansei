package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.TrackFormatStatus;

import java.util.UUID;

// filePath deliberately excluded, server-internal disk location, not something to leak to the frontend
// playCount/favorited are null unless resolved for a specific caller - null means "not checked", not "zero/false", so callers without an X-User-Id don't get a misleading 0/false
public record TrackFormatSummary(UUID id, String format, String quality, TrackFormatStatus status, Long fileSizeBytes, Integer playCount, Boolean favorited) {
    public static TrackFormatSummary from(TrackFormat format) {
        return new TrackFormatSummary(format.getId(), format.getFormat(), format.getQuality(), format.getStatus(), format.getFileSizeBytes(), null, null);
    }

    public static TrackFormatSummary from(TrackFormat format, int playCount, boolean favorited) {
        return new TrackFormatSummary(format.getId(), format.getFormat(), format.getQuality(), format.getStatus(), format.getFileSizeBytes(), playCount, favorited);
    }
}
