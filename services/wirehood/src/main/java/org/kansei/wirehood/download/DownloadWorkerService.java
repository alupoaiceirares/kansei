package org.kansei.wirehood.download;

import org.kansei.wirehood.messaging.DownloadJobMessage;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.TrackFormatStatus;
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
 * Consumes a download job: DOWNLOADING -> run yt-dlp (blocking, boundedElastic) -> READY (with the TrackFormat
 * row's filePath/fileSizeBytes filled in, plus the track's thumbnail_path) or FAILED. All state transitions land
 * on the TrackFormat row identified by (trackId, format) in the job message - not on the track itself, since a
 * track can have another format sitting READY/DOWNLOADING at the same time.
 * Exceptions are caught and mapped to FAILED here rather than propagated - letting them reach the RabbitMQ
 * listener would trigger a requeue-and-retry loop on a message that's likely to fail the same way every time.
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
                .zipWith(trackFormatRepository.findByTrackIdAndFormat(message.trackId(), message.format()))
                .flatMap(tuple -> markDownloading(tuple.getT2())
                        .flatMap(format -> runDownload(tuple.getT1(), format)
                                .onErrorResume(ex -> markFailed(tuple.getT1(), format))))
                .then();
    }

    private Mono<TrackFormat> markDownloading(TrackFormat format) {
        format.setStatus(TrackFormatStatus.DOWNLOADING);
        return trackFormatRepository.save(format);
    }

    private Mono<TrackFormat> runDownload(Track track, TrackFormat format) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(storageRoot);
                    Path outputBase = storageRoot.resolve(FilenameBuilder.baseName(track));
                    return downloadClient.download(track.getYoutubeVideoId(), outputBase, format.getFormat());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(files -> saveFormatAndMarkReady(track, format, files));
    }

    private Mono<TrackFormat> saveFormatAndMarkReady(Track track, TrackFormat format, YtDlpDownloadClient.DownloadedFiles files) {
        return Mono.fromCallable(() -> Files.size(files.mediaFile()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(sizeBytes -> {
                    format.setFilePath(files.mediaFile().toString());
                    format.setFileSizeBytes(sizeBytes);
                    format.setStatus(TrackFormatStatus.READY);
                    return trackFormatRepository.save(format);
                })
                .flatMap(savedFormat -> updateThumbnail(track, files)
                        .flatMap(savedTrack -> downloadService.fulfillReadyTrack(savedTrack, savedFormat.getFormat())
                                .thenReturn(savedFormat)));
    }

    // Track-level, format-independent - if two formats for the same track both download, this runs twice with the same result, harmless
    private Mono<Track> updateThumbnail(Track track, YtDlpDownloadClient.DownloadedFiles files) {
        track.setThumbnailPath(files.thumbnailFile() == null ? null : files.thumbnailFile().toString());
        track.setUpdatedAt(Instant.now());
        return trackRepository.save(track);
    }

    private Mono<TrackFormat> markFailed(Track track, TrackFormat format) {
        format.setStatus(TrackFormatStatus.FAILED);
        return trackFormatRepository.save(format)
                .flatMap(saved -> downloadService.notifyFailedTrack(track, saved.getFormat()).thenReturn(saved));
    }
}
