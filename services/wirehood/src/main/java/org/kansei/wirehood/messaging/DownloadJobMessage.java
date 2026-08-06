package org.kansei.wirehood.messaging;

import java.util.UUID;

/**
 * Payload for the download-jobs queue - just enough for the worker to look the track back up and run yt-dlp against it.
 */
public record DownloadJobMessage(UUID trackId, String youtubeVideoId) {
}
