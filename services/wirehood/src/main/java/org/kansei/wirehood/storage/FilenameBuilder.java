package org.kansei.wirehood.storage;

import org.kansei.wirehood.model.Track;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * `Artist_SongName_OtherStuff_{youtube_video_id}`
 * Artist/extra_info are dropped (not blanked) when null since the parser can legitimately leave them empty
 */
public final class FilenameBuilder {

    private FilenameBuilder() {
    }

    public static String baseName(Track track) {
        String joined = Stream.of(track.getArtist(), track.getTitle(), track.getExtraInfo())
                .filter(value -> value != null && !value.isBlank())
                .map(FilenameBuilder::sanitize)
                .collect(Collectors.joining("_"));
        return joined + "_" + track.getYoutubeVideoId();
    }

    private static String sanitize(String value) {
        return value.trim().replaceAll("[^a-zA-Z0-9]+", "_");
    }
}
