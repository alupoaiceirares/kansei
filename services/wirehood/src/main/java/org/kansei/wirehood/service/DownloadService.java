package org.kansei.wirehood.service;

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

    public DownloadService(
            TrackRepository trackRepository,
            UserLibraryRepository userLibraryRepository,
            DownloadRequestRepository downloadRequestRepository,
            DownloadJobPublisher downloadJobPublisher
    ) {
        this.trackRepository = trackRepository;
        this.userLibraryRepository = userLibraryRepository;
        this.downloadRequestRepository = downloadRequestRepository;
        this.downloadJobPublisher = downloadJobPublisher;
    }

    public Mono<SubmitDownloadResponse> submit(UUID userId, SubmitDownloadRequest request) {
        return trackRepository.findByYoutubeVideoId(request.youtubeVideoId())
                .flatMap(existing -> handleExisting(userId, existing))
                .switchIfEmpty(Mono.defer(() -> createAndQueue(userId, request)));
    }

    private Mono<SubmitDownloadResponse> handleExisting(UUID userId, Track track) {
        return switch (track.getStatus()) {
            case READY -> addToLibrary(userId, track);
            case PENDING, DOWNLOADING -> trackAsPending(userId, track);
            // treating a re-request of a permanently FAILED track as a retry rather than a dead end
            case FAILED -> retry(userId, track);
        };
    }

    private Mono<SubmitDownloadResponse> addToLibrary(UUID userId, Track track) {
        return userLibraryRepository.existsByUserIdAndTrackId(userId, track.getId())
                .flatMap(alreadyInLibrary -> alreadyInLibrary
                        ? Mono.just(track)
                        : userLibraryRepository.save(UserLibrary.builder()
                                .userId(userId)
                                .trackId(track.getId())
                                .addedAt(Instant.now())
                                .build()
                        ).thenReturn(track))
                .map(t -> new SubmitDownloadResponse(t.getId(), DownloadOutcome.ALREADY_READY));
    }

    private Mono<SubmitDownloadResponse> trackAsPending(UUID userId, Track track) {
        return saveDownloadRequest(userId, track.getId())
                .thenReturn(new SubmitDownloadResponse(track.getId(), DownloadOutcome.ALREADY_PENDING));
    }

    private Mono<SubmitDownloadResponse> retry(UUID userId, Track track) {
        track.setStatus(TrackStatus.PENDING);
        track.setUpdatedAt(Instant.now());
        return trackRepository.save(track)
                .flatMap(saved -> queue(userId, saved));
    }

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

    private Mono<SubmitDownloadResponse> queue(UUID userId, Track track) {
        return downloadJobPublisher.publish(track)
                .then(saveDownloadRequest(userId, track.getId()))
                .thenReturn(new SubmitDownloadResponse(track.getId(), DownloadOutcome.QUEUED));
    }

    private Mono<DownloadRequest> saveDownloadRequest(UUID userId, UUID trackId) {
        return downloadRequestRepository.save(DownloadRequest.builder()
                .userId(userId)
                .trackId(trackId)
                .requestedAt(Instant.now())
                .build());
    }
}
