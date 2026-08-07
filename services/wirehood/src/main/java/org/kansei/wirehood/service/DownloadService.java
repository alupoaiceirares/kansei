package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.DownloadEvent;
import org.kansei.wirehood.dto.DownloadOutcome;
import org.kansei.wirehood.dto.SubmitDownloadRequest;
import org.kansei.wirehood.dto.SubmitDownloadResponse;
import org.kansei.wirehood.messaging.DownloadJobPublisher;
import org.kansei.wirehood.model.DownloadRequest;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackStatus;
import org.kansei.wirehood.model.UserLibrary;
import org.kansei.wirehood.repository.DownloadRequestRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.kansei.wirehood.repository.UserLibraryRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform-wide dedup: one canonical track row per youtube_video_id, shared by every user
 */
@Service
public class DownloadService {

    private final TrackRepository trackRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final DownloadRequestRepository downloadRequestRepository;
    private final DownloadJobPublisher downloadJobPublisher;
    private final DownloadEventPublisher downloadEventPublisher;

    public DownloadService(
            TrackRepository trackRepository,
            UserLibraryRepository userLibraryRepository,
            DownloadRequestRepository downloadRequestRepository,
            DownloadJobPublisher downloadJobPublisher,
            DownloadEventPublisher downloadEventPublisher
    ) {
        this.trackRepository = trackRepository;
        this.userLibraryRepository = userLibraryRepository;
        this.downloadRequestRepository = downloadRequestRepository;
        this.downloadJobPublisher = downloadJobPublisher;
        this.downloadEventPublisher = downloadEventPublisher;
    }

    // Entry point for POST /wirehood/downloads - looks up the track by youtube_video_id, branches to existing-track handling or creates a brand new one
    public Mono<SubmitDownloadResponse> submit(UUID userId, SubmitDownloadRequest request) {
        return trackRepository.findByYoutubeVideoId(request.youtubeVideoId())
                .flatMap(existing -> handleExisting(userId, existing))
                .switchIfEmpty(Mono.defer(() -> createAndQueue(userId, request)));
    }

    // Track already exists somewhere in the pipeline - route by its current status
    private Mono<SubmitDownloadResponse> handleExisting(UUID userId, Track track) {
        return switch (track.getStatus()) {
            case READY -> addToLibrary(userId, track);
            case PENDING, DOWNLOADING -> trackAsPending(userId, track);
            // treating a re-request of a permanently FAILED track as a retry rather than a dead end
            case FAILED -> retry(userId, track);
        };
    }

    // Track is READY - just add it to this user's library (ALREADY_READY outcome), no worker call needed
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

    // Called by DownloadWorkerService once a track flips to READY - every user with an unacknowledged download_requests row on it gets auto-added to their library, the request marked acknowledged, and a READY event pushed to their SSE stream if one's open
    public Mono<Void> fulfillReadyTrack(Track track) {
        return downloadRequestRepository.findByTrackIdAndAcknowledgedAtIsNull(track.getId())
                .flatMap(request -> addTrackToLibraryIfAbsent(request.getUserId(), track.getId())
                        .then(acknowledge(request))
                        .doOnSuccess(v -> downloadEventPublisher.publish(
                                new DownloadEvent(request.getUserId(), track.getId(), TrackStatus.READY))))
                .then();
    }

    // Marks one download_requests row as handled, used only from fulfillReadyTrack
    private Mono<DownloadRequest> acknowledge(DownloadRequest request) {
        request.setAcknowledgedAt(Instant.now());
        return downloadRequestRepository.save(request);
    }

    // Called by DownloadWorkerService when a track permanently fails - every user still waiting on it gets a FAILED event, but the download_requests row stays unacknowledged so a later retry's eventual READY still fulfills it
    public Mono<Void> notifyFailedTrack(Track track) {
        return downloadRequestRepository.findByTrackIdAndAcknowledgedAtIsNull(track.getId())
                .doOnNext(request -> downloadEventPublisher.publish(
                        new DownloadEvent(request.getUserId(), track.getId(), TrackStatus.FAILED)))
                .then();
    }

    // Track is PENDING/DOWNLOADING already - just record this user as also waiting on it (ALREADY_PENDING outcome), worker will fulfill them later via fulfillReadyTrack
    private Mono<SubmitDownloadResponse> trackAsPending(UUID userId, Track track) {
        return saveDownloadRequest(userId, track.getId())
                .thenReturn(new SubmitDownloadResponse(track.getId(), DownloadOutcome.ALREADY_PENDING));
    }

    // Track permanently FAILED - flip it back to PENDING and re-queue as if it were a fresh request
    private Mono<SubmitDownloadResponse> retry(UUID userId, Track track) {
        track.setStatus(TrackStatus.PENDING);
        track.setUpdatedAt(Instant.now());
        return trackRepository.save(track)
                .flatMap(saved -> queue(userId, saved));
    }

    // Track doesn't exist yet - insert the canonical PENDING row, then queue it
    private Mono<SubmitDownloadResponse> createAndQueue(UUID userId, SubmitDownloadRequest request) {
        Instant now = Instant.now();
        Track track = Track.builder()
                .youtubeVideoId(request.youtubeVideoId())
                .title(request.title())
                .artist(request.artist())
                .extraInfo(request.extraInfo())
                .durationSeconds((int) request.durationSeconds())
                .status(TrackStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return trackRepository.save(track)
                .flatMap(saved -> queue(userId, saved))
                // Race: two users request the same brand-new video at once - the second insert hits the youtube_video_id UNIQUE constraint, fall back to the "already exists" path
                .onErrorResume(DuplicateKeyException.class, ex -> trackRepository
                        .findByYoutubeVideoId(request.youtubeVideoId())
                        .flatMap(existing -> handleExisting(userId, existing)));
    }

    // Publishes the RabbitMQ download job and records this user's download_requests row (QUEUED outcome)
    private Mono<SubmitDownloadResponse> queue(UUID userId, Track track) {
        return downloadJobPublisher.publish(track)
                .then(saveDownloadRequest(userId, track.getId()))
                .thenReturn(new SubmitDownloadResponse(track.getId(), DownloadOutcome.QUEUED));
    }

    // Shared insert used by both queue() (new/pending track) and trackAsPending() (already in-flight track)
    private Mono<DownloadRequest> saveDownloadRequest(UUID userId, UUID trackId) {
        return downloadRequestRepository.save(DownloadRequest.builder()
                .userId(userId)
                .trackId(trackId)
                .requestedAt(Instant.now())
                .build());
    }
}
