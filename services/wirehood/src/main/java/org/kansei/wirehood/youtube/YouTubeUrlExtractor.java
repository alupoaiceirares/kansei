package org.kansei.wirehood.youtube;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects a pasted YouTube link (as opposed to a plain search query) and pulls the video id out of it, so `SearchController` can route it to a direct lookup instead of a text search
 * Pasting a URL into `ytsearch:` would search for the URL's literal text, not resolve to the video
 */
public final class YouTubeUrlExtractor {

    // Covers watch?v=, youtu.be/, embed/, shorts/ - with or without scheme/www, extra query params after the id ignored
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)(?<id>[a-zA-Z0-9_-]{11})"
    );

    private YouTubeUrlExtractor() {
    }

    public static Optional<String> extractVideoId(String input) {
        if (input == null) {
            return Optional.empty();
        }
        Matcher matcher = VIDEO_ID_PATTERN.matcher(input);
        return matcher.find() ? Optional.of(matcher.group("id")) : Optional.empty();
    }
}
