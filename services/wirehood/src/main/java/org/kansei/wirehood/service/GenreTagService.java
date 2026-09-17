package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.GenreVoteResponse;
import org.kansei.wirehood.messaging.AuditPublisher;
import org.kansei.wirehood.repository.TrackGenreTagRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Crowd-tagging: any user with the track in their library can tag/re-tag it with one or more genres - one vote per
 * (track, genre, user)
 */
@Service
public class GenreTagService {

    private final TrackGenreTagRepository trackGenreTagRepository;
    private final AdminAuthService adminAuthService;
    private final AuditPublisher auditPublisher;

    public GenreTagService(TrackGenreTagRepository trackGenreTagRepository, AdminAuthService adminAuthService, AuditPublisher auditPublisher) {
        this.trackGenreTagRepository = trackGenreTagRepository;
        this.adminAuthService = adminAuthService;
        this.auditPublisher = auditPublisher;
    }

    // concatMap, not flatMap - running these concurrently let the R2DBC Postgres driver batch multiple upsertTag() calls onto one executeMany() and tangle the parameter binds (observed: a genre_id landing null). Sequential is safe and this isn't a hot path.
    public Mono<Void> tag(UUID trackId, List<UUID> genreIds, UUID userId) {
        Instant taggedAt = Instant.now();
        return Flux.fromIterable(genreIds)
                .concatMap(genreId -> trackGenreTagRepository.upsertTag(trackId, genreId, userId, taggedAt))
                .then();
    }

    public Flux<GenreVoteResponse> tagsForTrack(UUID trackId) {
        return trackGenreTagRepository.countVotesByTrackId(trackId);
    }

    // Admin-only moderation action, bad-faith tagging the crowd-vote hasn't corrected yet
    public Mono<Void> removeTag(UUID trackId, UUID genreId, UUID adminUserId, String adminRole) {
        return adminAuthService.requireAdmin(adminUserId, adminRole)
                .then(trackGenreTagRepository.deleteTag(trackId, genreId))
                .then(auditPublisher.publishWithResolvedUsername("REMOVE_GENRE_TAG", adminUserId, "TRACK", trackId.toString(),
                        Map.of("genreId", genreId.toString())));
    }
}
