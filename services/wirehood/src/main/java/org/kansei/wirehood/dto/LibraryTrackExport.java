package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.Track;

import java.util.List;

public record LibraryTrackExport(String title, String artist, String extraInfo, List<FormatExport> formats) {
    public static LibraryTrackExport of(Track track, List<FormatExport> formats) {
        return new LibraryTrackExport(track.getTitle(), track.getArtist(), track.getExtraInfo(), formats);
    }
}
