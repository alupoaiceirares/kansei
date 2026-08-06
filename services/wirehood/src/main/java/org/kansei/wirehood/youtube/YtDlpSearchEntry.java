package org.kansei.wirehood.youtube;

import java.util.List;

/**
 * Shape of one line of `yt-dlp --dump-json --flat-playlist` output for a `ytsearch:` query `channel`/`uploader` both mapped since which one's populated varies by yt-dlp version
 */
record YtDlpSearchEntry(
        String id,
        String title,
        String channel,
        String uploader,
        Double duration,
        List<Thumbnail> thumbnails
) {

    record Thumbnail(String url) {
    }
}
