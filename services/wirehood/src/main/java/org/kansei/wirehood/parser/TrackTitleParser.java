package org.kansei.wirehood.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic regex rules for common YouTube title patterns ("Artist - Song (Official Video)", "Song by Artist"), falling back to the raw title as-is (artist blank) when nothing matches
 * A static utility, pure function: raw YouTube title in, best-guess split out (artist/title/extra_info). Regex rules live here
 */
public final class TrackTitleParser {

    private static final Pattern ARTIST_DASH_TITLE = Pattern.compile("^(?<artist>[^-]+?)\\s*-\\s*(?<rest>.+)$");
    private static final Pattern SONG_BY_ARTIST = Pattern.compile("^(?<title>.+?)\\s+by\\s+(?<artist>.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_EXTRA = Pattern.compile("^(?<title>.+?)\\s*[(\\[](?<extra>[^)\\]]+)[)\\]]\\s*$");

    private TrackTitleParser() {
    }

    public static ParsedTitle parse(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return new ParsedTitle(null, "", null);
        }
        String working = rawTitle.trim();

        Matcher dash = ARTIST_DASH_TITLE.matcher(working);
        if (dash.matches()) {
            return splitExtra(dash.group("artist").trim(), dash.group("rest").trim());
        }

        Matcher by = SONG_BY_ARTIST.matcher(working);
        if (by.matches()) {
            return splitExtra(by.group("artist").trim(), by.group("title").trim());
        }

        return splitExtra(null, working);
    }

    private static ParsedTitle splitExtra(String artist, String titleWithMaybeExtra) {
        Matcher extraMatch = TRAILING_EXTRA.matcher(titleWithMaybeExtra);
        if (extraMatch.matches()) {
            return new ParsedTitle(artist, extraMatch.group("title").trim(), extraMatch.group("extra").trim());
        }
        return new ParsedTitle(artist, titleWithMaybeExtra.trim(), null);
    }
}
