package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.DownloadEvent;
import org.kansei.wirehood.dto.DownloadOutcome;
import org.kansei.wirehood.dto.SubmitDownloadRequest;
import org.kansei.wirehood.dto.SubmitDownloadResponse;
import org.kansei.wirehood.messaging.DownloadJobPublisher;
import org.kansei.wirehood.model.DownloadRequest;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.TrackFormatStatus;
import org.kansei.wirehood.model.UserLibrary;
import org.kansei.wirehood.repository.DownloadRequestRepository;
import org.kansei.wirehood.repository.TrackFormatRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.kansei.wirehood.repository.UserLibraryRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform-wide dedup: one canonical track row per youtube_video_id, shared by every user - but readiness is
 * per (track, format), not per track. Dedup/queue decisions key off TrackFormat, never a single track-level status.
 */
@Service
public class DownloadService {

    private final TrackRepository trackRepository;
    private final TrackFormatRepository trackFormatRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final DownloadRequestRepository downloadRequestRepository;
    private final DownloadJobPublisher downloadJobPublisher;
    private final DownloadEventPublisher downloadEventPublisher;

    public DownloadService(
            TrackRepository trackRepository,
            TrackFormatRepository trackFormatRepository,
            UserLibraryRepository userLibraryRepository,
            DownloadRequestRepository downloadRequestRepository,
            DownloadJobPublisher downloadJobPublisher,
            DownloadEventPublisher downloadEventPublisher
    ) {
        this.trackRepository = trackRepository;
        this.trackFormatRepository = trackFormatRepository;
        this.userLibraryRepository = userLibraryRepository;
        this.downloadRequestRepository = downloadRequestRepository;
        this.downloadJobPublisher = downloadJobPublisher;
        this.downloadEventPublisher = downloadEventPublisher;
    }

    // Entry point for POST /wirehood/downloads - looks up the track by youtube_video_id, branches to existing-track handling or creates a brand new one
    public Mono<SubmitDownloadResponse> submit(UUID userId, SubmitDownloadRequest request) {
        return trackRepository.findByYoutubeVideoId(request.youtubeVideoId())
                .flatMap(existing -> handleExistingTrack(userId, existing, request))
                .switchIfEmpty(Mono.defer(() -> createTrackAndQueue(userId, request)));
    }

    // Track already exists - route by whether the SPECIFIC requested format has been attempted yet
    private Mono<SubmitDownloadResponse> handleExistingTrack(UUID userId, Track track, SubmitDownloadRequest request) {
        return trackFormatRepository.findByTrackIdAndFormat(track.getId(), request.format())
                .flatMap(existingFormat -> handleExistingFormat(userId, track, existingFormat))
                .switchIfEmpty(Mono.defer(() -> queueFormat(userId, track, request.format())));
    }

    // This exact (track, format) has been attempted before - route by its current status
    private Mono<SubmitDownloadResponse> handleExistingFormat(UUID userId, Track track, TrackFormat format) {
        return switch (format.getStatus()) {
            case READY -> addToLibrary(userId, track);
            case PENDING, DOWNLOADING -> trackAsPending(userId, track, format.getFormat());
            // treating a re-request of a permanently FAILED format as a retry rather than a dead end
            case FAILED -> retryFormat(userId, track, format);
        };
    }

    // Format is READY - just add the track to this user's library (ALREADY_READY outcome), no worker call needed
    private Mono<SubmitDownloadResponse> addToLibrary(UUID userId, Track track) {
        return addTrackToLibraryIfAbsent(userId, track.getId())
                .thenReturn(new SubmitDownloadResponse(track.getId(), DownloadOutcome.ALREADY_READY));
    }

    // Shared idempotent insert - used both by the single-user addToLibrary path above and the multi-user fulfillReadyTrack path below
    private Mono<Void> addTrackToLibraryIfAbsent(UUID userId, UUID trackId) {
        return userLibraryRepository.existsByUserIdAndTrackId(userId, trackId)
                .flatMap(alreadyInLibrary -> alreadyInLibrary
                        ? Mono.empty()
                        : userLibraryRepository.save(UserLibrary.builder()
                                .userId(userId)
                                .trackId(trackId)
                                .addedAt(Instant.now())
                                .build()))
                .then();
    }

    // Called by DownloadWorkerService once a (track, format) flips to READY - every user with an unacknowledged
    // download_requests row on that exact (track, format) gets auto-added to their library, the request marked
    // acknowledged, and a READY event pushed to their SSE stream if one's open
    public Mono<Void> fulfillReadyTrack(Track track, String format) {
        return downloadRequestRepository.findByTrackIdAndFormatAndAcknowledgedAtIsNull(track.getId(), format)
                .flatMap(request -> addTrackToLibraryIfAbsent(request.getUserId(), track.getId())
                        .then(acknowledge(request))
                        .doOnSuccess(v -> downloadEventPublisher.publish(
                                new DownloadEvent(request.getUserId(), track.getId(), format, TrackFormatStatus.READY))))
                .then();
    }

    // Marks one download_requests row as handled, used only from fulfillReadyTrack
    private Mono<DownloadRequest> acknowledge(DownloadRequest request) {
        request.setAcknowledgedAt(Instant.now());
        return downloadRequestRepository.save(request);
    }

    // Called by DownloadWorkerService when a (track, format) permanently fails - every user still waiting on
    // that exact format gets a FAILED event, but the download_requests row stays unacknowledged so a later
    // retry's eventual READY still fulfills it
    public Mono<Void> notifyFailedTrack(Track track, String format) {
        return downloadRequestRepository.findByTrackIdAndFormatAndAcknowledgedAtIsNull(track.getId(), format)
                .doOnNext(request -> downloadEventPublisher.publish(
                        new DownloadEvent(request.getUserId(), track.getId(), format, TrackFormatStatus.FAILED)))
                .then();
    }

    // This (track, format) is PENDING/DOWNLOADING already - just record this user as also waiting on it (ALREADY_PENDING outcome), worker will fulfill them later via fulfillReadyTrack
    private Mono<SubmitDownloadResponse> trackAsPending(UUID userId, Track track, String format) {
        return saveDownloadRequest(userId, track.getId(), format)
                .thenReturn(new SubmitDownloadResponse(track.getId(), DownloadOutcome.ALREADY_PENDING));
    }

    // This (track, format) permanently FAILED - flip it back to PENDING and re-queue as if it were a fresh request
    private Mono<SubmitDownloadResponse> retryFormat(UUID userId, Track track, TrackFormat format) {
        format.setStatus(TrackFormatStatus.PENDING);
        return trackFormatRepository.save(format)
                .flatMap(saved -> queue(userId, track, saved.getFormat()));
    }

    // Track doesn't exist yet - insert the canonical metadata row, then queue the requested format on it
    private Mono<SubmitDownloadResponse> createTrackAndQueue(UUID userId, SubmitDownloadRequest request) {
        Instant now = Instant.now();
        Track track = Track.builder()
                .youtubeVideoId(request.youtubeVideoId())
                .title(request.title())
                .artist(request.artist())
                .extraInfo(request.extraInfo())
                .durationSeconds((int) request.durationSeconds())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return trackRepository.save(track)
                .flatMap(saved -> queueFormat(userId, saved, request.format()))
                // Race: two users request the same brand-new video at once - the second insert hits the youtube_video_id UNIQUE constraint, fall back to the "already exists" path
                .onErrorResume(DuplicateKeyException.class, ex -> trackRepository
                        .findByYoutubeVideoId(request.youtubeVideoId())
                        .flatMap(existing -> handleExistingTrack(userId, existing, request)));
    }

    // Track exists but this exact format has never been attempted - insert a fresh PENDING TrackFormat row and queue it
    private Mono<SubmitDownloadResponse> queueFormat(UUID userId, Track track, String format) {
        TrackFormat pending = TrackFormat.builder()
                .trackId(track.getId())
                .format(format)
                .quality(defaultQualityFor(format))
                .status(TrackFormatStatus.PENDING)
                .build();

        return trackFormatRepository.save(pending)
                .flatMap(saved -> queue(userId, track, saved.getFormat()))
                // Race: two requests for the same (track, format) at once - the second insert hits the new UNIQUE(track_id, format) constraint, fall back to the "already exists" path
                .onErrorResume(DuplicateKeyException.class, ex -> trackFormatRepository
                        .findByTrackIdAndFormat(track.getId(), format)
                        .flatMap(existing -> handleExistingFormat(userId, track, existing)));
    }

    // Publishes the RabbitMQ download job and records this user's download_requests row (QUEUED outcome)
    private Mono<SubmitDownloadResponse> queue(UUID userId, Track track, String format) {
        return downloadJobPublisher.publish(track, format)
                .then(saveDownloadRequest(userId, track.getId(), format))
                .thenReturn(new SubmitDownloadResponse(track.getId(), DownloadOutcome.QUEUED));
    }

    // Shared insert used by both queue() (new/pending format) and trackAsPending() (already in-flight format)
    private Mono<DownloadRequest> saveDownloadRequest(UUID userId, UUID trackId, String format) {
        return downloadRequestRepository.save(DownloadRequest.builder()
                .userId(userId)
                .trackId(trackId)
                .format(format)
                .requestedAt(Instant.now())
                .build());
    }

    // Placeholder until real quality selection is built - matches the format itself for now, same as before this change
    private String defaultQualityFor(String format) {
        return "mp4".equals(format) ? "video" : "audio";
    }
}
