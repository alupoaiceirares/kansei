package org.kansei.wirehood.download;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Actual `yt-dlp` + FFmpeg download - separate client from `YtDlpSearchClient`, different command entirely (extraction + thumbnail conversion vs. a search query).
 * Single format for now (mp3 audio) - multi-format/quality support is a follow-up, not built in this pass
 */
@Component
public class YtDlpDownloadClient {

    public DownloadedFiles download(String youtubeVideoId, Path outputBasePath) throws IOException, InterruptedException {
        String url = "https://www.youtube.com/watch?v=" + youtubeVideoId;
        String outputTemplate = outputBasePath + ".%(ext)s";

        Process process = new ProcessBuilder(
                "yt-dlp", url,
                "-x", "--audio-format", "mp3",
                "--write-thumbnail", "--convert-thumbnails", "jpg",
                "--no-warnings",
                "-o", outputTemplate
        ).redirectErrorStream(true).start();

        // Drain stdout so yt-dlp's progress output can't fill the pipe buffer and stall the process
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                // discarded - not needed here, worth capturing to a log line later if this gets noisy
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("yt-dlp download exited with code " + exitCode + " for video: " + youtubeVideoId);
        }

        Path audioFile = Path.of(outputBasePath + ".mp3");
        Path thumbnailFile = Path.of(outputBasePath + ".jpg");

        if (!Files.exists(audioFile)) {
            throw new IllegalStateException("yt-dlp reported success but expected output file is missing: " + audioFile);
        }

        return new DownloadedFiles(audioFile, Files.exists(thumbnailFile) ? thumbnailFile : null);
    }

    public record DownloadedFiles(Path audioFile, Path thumbnailFile) {
    }
}
