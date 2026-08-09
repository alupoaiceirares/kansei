package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.Track;

import java.util.UUID;

public record PlaylistTrackItem(
        UUID trackId,
        String title,
        String artist,
        String extraInfo,
        Integer durationSeconds,
        int position
) {
    public static PlaylistTrackItem from(Track track, int position) {
        return new PlaylistTrackItem(
                track.getId(),
                track.getTitle(),
                track.getArtist(),
                track.getExtraInfo(),
                track.getDurationSeconds(),
                position
        );
    }
}
