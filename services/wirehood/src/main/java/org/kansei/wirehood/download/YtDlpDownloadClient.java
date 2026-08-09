package org.kansei.wirehood.download;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Actual `yt-dlp` + FFmpeg download - separate client from `YtDlpSearchClient`, different command entirely (extraction + thumbnail conversion vs. a search query).
 * format is "mp3" (audio-only extraction) or "mp4" (real video, muxed audio+video) - output extension differs
 * per format off the same output template, so a track's mp3 and mp4 files never collide on disk.
 */
@Component
public class YtDlpDownloadClient {

    public DownloadedFiles download(String youtubeVideoId, Path outputBasePath, String format) throws IOException, InterruptedException {
        String url = "https://www.youtube.com/watch?v=" + youtubeVideoId;
        String outputTemplate = outputBasePath + ".%(ext)s";
        String expectedExtension = "mp4".equals(format) ? "mp4" : "mp3";

        List<String> command = new ArrayList<>(List.of("yt-dlp", url));
        if ("mp4".equals(format)) {
            // Best available video+audio, remuxed into mp4 - falls back to a pre-muxed mp4 stream if a separate best-video/best-audio pair isn't available
            command.addAll(List.of("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]"));
        } else {
            command.addAll(List.of("-x", "--audio-format", "mp3"));
        }
        command.addAll(List.of(
                "--write-thumbnail", "--convert-thumbnails", "jpg",
                "--no-warnings",
                "-o", outputTemplate
        ));

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

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

        Path mediaFile = Path.of(outputBasePath + "." + expectedExtension);
        Path thumbnailFile = Path.of(outputBasePath + ".jpg");

        if (!Files.exists(mediaFile)) {
            throw new IllegalStateException("yt-dlp reported success but expected output file is missing: " + mediaFile);
        }

        return new DownloadedFiles(mediaFile, Files.exists(thumbnailFile) ? thumbnailFile : null);
    }

    public record DownloadedFiles(Path mediaFile, Path thumbnailFile) {
    }
}
