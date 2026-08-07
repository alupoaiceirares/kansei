package org.kansei.wirehood.download;

import org.kansei.wirehood.messaging.DownloadJobMessage;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.TrackStatus;
import org.kansei.wirehood.repository.TrackFormatRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.kansei.wirehood.service.DownloadService;
import org.kansei.wirehood.storage.FilenameBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Consumes a download job: DOWNLOADING -> run yt-dlp (blocking, boundedElastic) -> READY (with a TrackFormat row + thumbnail_path) or FAILED
 * Exceptions are caught and mapped to FAILED here rather than propagated - letting them reach the RabbitMQ listener would trigger a requeue-and-retry loop on a message that's likely to fail the same way every time
 */
@Service
public class DownloadWorkerService {

    private final TrackRepository trackRepository;
    private final TrackFormatRepository trackFormatRepository;
    private final YtDlpDownloadClient downloadClient;
    private final DownloadService downloadService;
    private final Path storageRoot;

    public DownloadWorkerService(
            TrackRepository trackRepository,
            TrackFormatRepository trackFormatRepository,
            YtDlpDownloadClient downloadClient,
            DownloadService downloadService,
            @Value("${wirehood.storage.root-dir}") String storageRootDir
    ) {
        this.trackRepository = trackRepository;
        this.trackFormatRepository = trackFormatRepository;
        this.downloadClient = downloadClient;
        this.downloadService = downloadService;
        this.storageRoot = Path.of(storageRootDir);
    }

    public Mono<Void> process(DownloadJobMessage message) {
        return trackRepository.findById(message.trackId())
                .flatMap(this::markDownloading)
                .flatMap(track -> runDownload(track).onErrorResume(ex -> markFailed(track)))
                .then();
    }

    private Mono<Track> markDownloading(Track track) {
        track.setStatus(TrackStatus.DOWNLOADING);
        track.setUpdatedAt(Instant.now());
        return trackRepository.save(track);
    }

    private Mono<Track> runDownload(Track track) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(storageRoot);
                    Path outputBase = storageRoot.resolve(FilenameBuilder.baseName(track));
                    return downloadClient.download(track.getYoutubeVideoId(), outputBase);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(files -> saveFormatAndMarkReady(track, files));
    }

    private Mono<Track> saveFormatAndMarkReady(Track track, YtDlpDownloadClient.DownloadedFiles files) {
        return Mono.fromCallable(() -> Files.size(files.audioFile()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(sizeBytes -> trackFormatRepository.save(TrackFormat.builder()
                        .trackId(track.getId())
                        .format("mp3")
                        // Real bitrate/quality isn't parsed out of yt-dlp's output yet - fixed placeholder until multi-format/quality support is built
                        .quality("audio")
                        .filePath(files.audioFile().toString())
                        .fileSizeBytes(sizeBytes)
                        .build()))
                .then(Mono.defer(() -> {
                    track.setStatus(TrackStatus.READY);
                    track.setThumbnailPath(files.thumbnailFile() == null ? null : files.thumbnailFile().toString());
                    track.setUpdatedAt(Instant.now());
                    return trackRepository.save(track);
                }))
                .flatMap(saved -> downloadService.fulfillReadyTrack(saved).thenReturn(saved));
    }

    private Mono<Track> markFailed(Track track) {
        track.setStatus(TrackStatus.FAILED);
        track.setUpdatedAt(Instant.now());
        return trackRepository.save(track);
    }
}
