package org.kansei.wirehood.youtube;

/**
 * Shape of `yt-dlp <url> --dump-json --no-playlist` output for a single direct video lookup different from YtDlpSearchEntry's shape (`thumbnail` is one URL here, not a `thumbnails` list, since this isn't the flat/search extraction path)
 */
record YtDlpVideoInfo(
        String id,
        String title,
        String channel,
        String uploader,
        Double duration,
        String thumbnail
) {
}
