package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.ThumbnailSubmissionResponse;
import org.kansei.wirehood.model.ThumbnailSubmissionStatus;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackThumbnailSubmission;
import org.kansei.wirehood.repository.TrackRepository;
import org.kansei.wirehood.repository.TrackThumbnailSubmissionRepository;
import org.kansei.wirehood.storage.FilenameBuilder;
import org.kansei.wirehood.storage.ImageMagicBytes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

@Service
public class ThumbnailSubmissionService {

    // Thumbnails are small - 3MB is generous headroom, not a generic upload limit
    private static final int MAX_SIZE_BYTES = 3 * 1024 * 1024;

    private final TrackRepository trackRepository;
    private final TrackThumbnailSubmissionRepository submissionRepository;
    private final AdminAuthService adminAuthService;
    private final Path storageRoot;
    private final Path submissionsDir;

    public ThumbnailSubmissionService(
            TrackRepository trackRepository,
            TrackThumbnailSubmissionRepository submissionRepository,
            AdminAuthService adminAuthService,
            @Value("${wirehood.storage.root-dir}") String storageRootDir
    ) {
        this.trackRepository = trackRepository;
        this.submissionRepository = submissionRepository;
        this.adminAuthService = adminAuthService;
        this.storageRoot = Path.of(storageRootDir);
        this.submissionsDir = this.storageRoot.resolve("thumbnail-submissions");
    }

    // Any wirehood user, no dedup/lock - multiple simultaneous pending submissions per track are allowed on purpose
    public Mono<ThumbnailSubmissionResponse> submit(UUID trackId, UUID userId, FilePart filePart) {
        return trackRepository.findById(trackId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found")))
                .then(readAndValidate(filePart))
                .flatMap(bytes -> {
                    String extension = detectExtensionOrReject(bytes);
                    return writeToSubmissionsDir(bytes, extension)
                            .flatMap(path -> submissionRepository.save(TrackThumbnailSubmission.builder()
                                            .trackId(trackId)
                                            .submittedBy(userId)
                                            .filePath(path.toString())
                                            .status(ThumbnailSubmissionStatus.PENDING)
                                            .submittedAt(Instant.now())
                                            .build())
                                    .map(ThumbnailSubmissionResponse::from)
                                    // DB insert failed after the file was already written - clean up the orphan rather than leaving it stranded
                                    .onErrorResume(ex -> deleteFileIfPresent(path).then(Mono.error(ex))));
                });
    }

    public Flux<ThumbnailSubmissionResponse> listByStatus(UUID adminUserId, ThumbnailSubmissionStatus status) {
        return adminAuthService.requireAdmin(adminUserId)
                .flatMapMany(admin -> submissionRepository.findByStatus(status))
                .map(ThumbnailSubmissionResponse::from);
    }

    // Lets an admin actually see the image before approving/rejecting it - same FileSystemResource pattern as TrackService.getThumbnail
    public Mono<ResponseEntity<Resource>> getFile(UUID adminUserId, UUID submissionId) {
        return adminAuthService.requireAdmin(adminUserId)
                .then(findByIdOr404(submissionId))
                .map(submission -> {
                    Path path = Path.of(submission.getFilePath());
                    MediaType mediaType = path.toString().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
                    return ResponseEntity.ok().contentType(mediaType).body((Resource) new FileSystemResource(path));
                });
    }

    public Mono<Void> approve(UUID submissionId, UUID adminUserId) {
        return adminAuthService.requireAdmin(adminUserId)
                .then(findPendingOr409(submissionId))
                .flatMap(submission -> trackRepository.findById(submission.getTrackId())
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found")))
                        .flatMap(track -> applyApproval(track, submission, adminUserId)));
    }

    private Mono<Void> applyApproval(Track track, TrackThumbnailSubmission submission, UUID adminUserId) {
        String oldThumbnailPath = track.getThumbnailPath();
        return moveIntoStorageRoot(track, submission)
                .flatMap(newPath -> {
                    track.setThumbnailPath(newPath.toString());
                    track.setUpdatedAt(Instant.now());
                    return trackRepository.save(track);
                })
                .flatMap(savedTrack -> deleteFileIfPresent(oldThumbnailPath == null ? null : Path.of(oldThumbnailPath))
                        .then(markReviewed(submission, ThumbnailSubmissionStatus.APPROVED, adminUserId))
                        .then(autoRejectOthers(savedTrack.getId(), submission.getId(), adminUserId)));
    }

    public Mono<Void> reject(UUID submissionId, UUID adminUserId) {
        return adminAuthService.requireAdmin(adminUserId)
                .then(findPendingOr409(submissionId))
                .flatMap(submission -> deleteFileIfPresent(Path.of(submission.getFilePath()))
                        .then(markReviewed(submission, ThumbnailSubmissionStatus.REJECTED, adminUserId)));
    }

    // concatMap, not flatMap - same R2DBC concurrent-write binding issue documented in GenreTagService.tag
    private Mono<Void> autoRejectOthers(UUID trackId, UUID excludeSubmissionId, UUID adminUserId) {
        return submissionRepository.findByTrackIdAndStatus(trackId, ThumbnailSubmissionStatus.PENDING)
                .filter(s -> !s.getId().equals(excludeSubmissionId))
                .concatMap(s -> deleteFileIfPresent(Path.of(s.getFilePath()))
                        .then(markReviewed(s, ThumbnailSubmissionStatus.REJECTED, adminUserId)))
                .then();
    }

    private Mono<Void> markReviewed(TrackThumbnailSubmission submission, ThumbnailSubmissionStatus status, UUID adminUserId) {
        submission.setStatus(status);
        submission.setReviewedAt(Instant.now());
        submission.setReviewedBy(adminUserId);
        return submissionRepository.save(submission).then();
    }

    private Mono<TrackThumbnailSubmission> findByIdOr404(UUID submissionId) {
        return submissionRepository.findById(submissionId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Thumbnail submission not found")));
    }

    private Mono<TrackThumbnailSubmission> findPendingOr409(UUID submissionId) {
        return findByIdOr404(submissionId)
                .flatMap(submission -> submission.getStatus() == ThumbnailSubmissionStatus.PENDING
                        ? Mono.just(submission)
                        : Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Submission already reviewed")));
    }

    // Bounded join - fails fast on an oversized upload instead of buffering the whole body first
    private Mono<byte[]> readAndValidate(FilePart filePart) {
        return DataBufferUtils.join(filePart.content(), MAX_SIZE_BYTES)
                .onErrorMap(DataBufferLimitException.class, ex ->
                        new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Thumbnail must be 3MB or smaller"))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                });
    }

    // Magic bytes are the only signal trusted for the real file type - the declared Content-Type/filename is never trusted
    private String detectExtensionOrReject(byte[] bytes) {
        return ImageMagicBytes.detectExtension(bytes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be a valid JPEG or PNG image"));
    }

    private Mono<Path> writeToSubmissionsDir(byte[] bytes, String extension) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(submissionsDir);
                    Path path = submissionsDir.resolve(UUID.randomUUID() + "." + extension);
                    Files.write(path, bytes);
                    return path;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // Renamed on move so a re-approved track (old approved file already deleted) never collides with a stale filename
    private Mono<Path> moveIntoStorageRoot(Track track, TrackThumbnailSubmission submission) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(storageRoot);
                    Path source = Path.of(submission.getFilePath());
                    String extension = extensionOf(source);
                    Path target = storageRoot.resolve(
                            FilenameBuilder.baseName(track) + "_thumbnail_" + submission.getId() + "." + extension);
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    return target;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "jpg" : name.substring(dot + 1);
    }

    // Best-effort, fail-soft - a leftover file on disk is a cheaper failure mode than blocking the caller's request over cleanup
    private Mono<Void> deleteFileIfPresent(Path path) {
        if (path == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> Files.deleteIfExists(path))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> Mono.just(false))
                .then();
    }
}
