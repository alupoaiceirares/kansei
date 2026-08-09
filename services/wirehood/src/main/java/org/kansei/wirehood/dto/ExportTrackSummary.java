package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.Track;

// Musical-data angle only, deliberately minimal
public record ExportTrackSummary(String title, String artist, String extraInfo) {
    public static ExportTrackSummary from(Track track) {
        return new ExportTrackSummary(track.getTitle(), track.getArtist(), track.getExtraInfo());
    }
}
