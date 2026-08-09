package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.Track;

import java.time.LocalDate;
import java.util.UUID;

public record SongOfTheDayResponse(
        LocalDate day,
        UUID trackId,
        String title,
        String artist,
        String extraInfo,
        Integer durationSeconds
) {
    public static SongOfTheDayResponse of(LocalDate day, Track track) {
        return new SongOfTheDayResponse(day, track.getId(), track.getTitle(), track.getArtist(), track.getExtraInfo(), track.getDurationSeconds());
    }
}
