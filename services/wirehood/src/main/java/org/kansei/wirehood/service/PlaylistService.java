package org.kansei.wirehood.service;

import org.kansei.wirehood.client.ShieldwallUserClient;
import org.kansei.wirehood.dto.AddCollaboratorRequest;
import org.kansei.wirehood.dto.AddTrackRequest;
import org.kansei.wirehood.dto.CollaboratorResponse;
import org.kansei.wirehood.dto.CreatePlaylistRequest;
import org.kansei.wirehood.dto.PlaylistDetailResponse;
import org.kansei.wirehood.dto.PlaylistResponse;
import org.kansei.wirehood.dto.PlaylistTrackItem;
import org.kansei.wirehood.dto.ReorderPlaylistRequest;
import org.kansei.wirehood.dto.UpdatePlaylistRequest;
import org.kansei.wirehood.model.Playlist;
import org.kansei.wirehood.model.PlaylistCollaborator;
import org.kansei.wirehood.model.PlaylistTrack;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.repository.PlaylistCollaboratorRepository;
import org.kansei.wirehood.repository.PlaylistRepository;
import org.kansei.wirehood.repository.PlaylistTrackRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final PlaylistCollaboratorRepository playlistCollaboratorRepository;
    private final TrackRepository trackRepository;
    private final ShieldwallUserClient shieldwallUserClient;

    public PlaylistService(
            PlaylistRepository playlistRepository,
            PlaylistTrackRepository playlistTrackRepository,
            PlaylistCollaboratorRepository playlistCollaboratorRepository,
            TrackRepository trackRepository,
            ShieldwallUserClient shieldwallUserClient
    ) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.playlistCollaboratorRepository = playlistCollaboratorRepository;
        this.trackRepository = trackRepository;
        this.shieldwallUserClient = shieldwallUserClient;
    }

    // ownerUsername is always null here, not "Unknown user" - the owner IS the caller, who already knows their own username client-side, so there's no reason to round-trip to shieldwall for it
    public Mono<PlaylistResponse> create(UUID userId, CreatePlaylistRequest request) {
        Playlist playlist = Playlist.builder()
                .ownerId(userId)
                .name(request.name())
                .shared(request.shared())
                .createdAt(Instant.now())
                .build();
        return playlistRepository.save(playlist)
                .map(saved -> new PlaylistResponse(saved.getId(), saved.getOwnerId(), null, saved.getName(), saved.isShared(), saved.getCreatedAt(), 0));
    }

    // Owned by me OR I'm a collaborator - one batch username lookup for every distinct owner in the list (not one call per playlist), and only because other people's playlists can show up here too
    public Flux<PlaylistResponse> listAccessiblePlaylists(UUID userId) {
        return playlistRepository.findAccessibleByUserId(userId)
                .collectList()
                .flatMapMany(playlists -> {
                    List<UUID> ownerIds = playlists.stream().map(Playlist::getOwnerId).distinct().collect(Collectors.toList());
                    return shieldwallUserClient.resolveUsernames(ownerIds)
                            .flatMapMany(usernames -> Flux.fromIterable(playlists)
                                    .flatMap(playlist -> playlistTrackRepository.countByPlaylistId(playlist.getId())
                                            .map(count -> PlaylistResponse.from(playlist, usernames.get(playlist.getOwnerId()), count))));
                });
    }

    public Mono<PlaylistDetailResponse> getPlaylistDetail(UUID playlistId, UUID userId) {
        return requireAccess(playlistId, userId)
                .flatMap(playlist -> Mono.zip(
                                playlistTrackRepository.findByPlaylistIdOrderByPosition(playlistId).collectList(),
                                playlistCollaboratorRepository.findByPlaylistId(playlistId).collectList()
                        )
                        .flatMap(tuple -> {
                            List<PlaylistTrack> playlistTracks = tuple.getT1();
                            List<PlaylistCollaborator> collaborators = tuple.getT2();

                            List<UUID> trackIds = playlistTracks.stream().map(PlaylistTrack::getTrackId).collect(Collectors.toList());
                            List<UUID> usernameIds = collaborators.stream().map(PlaylistCollaborator::getUserId).collect(Collectors.toList());
                            usernameIds.add(playlist.getOwnerId());

                            Mono<Map<UUID, Track>> tracksMono = trackRepository.findAllById(trackIds)
                                    .collectMap(Track::getId);
                            Mono<Map<UUID, String>> usernamesMono = shieldwallUserClient.resolveUsernames(usernameIds);

                            return Mono.zip(tracksMono, usernamesMono).map(resolved -> {
                                Map<UUID, Track> trackById = resolved.getT1();
                                Map<UUID, String> usernames = resolved.getT2();

                                List<PlaylistTrackItem> items = playlistTracks.stream()
                                        .map(pt -> PlaylistTrackItem.from(trackById.get(pt.getTrackId()), pt.getPosition()))
                                        .collect(Collectors.toList());
                                List<CollaboratorResponse> collaboratorResponses = collaborators.stream()
                                        .map(c -> CollaboratorResponse.from(c.getUserId(), c.getAddedAt(), usernames.get(c.getUserId())))
                                        .collect(Collectors.toList());

                                return PlaylistDetailResponse.from(playlist, usernames.get(playlist.getOwnerId()), items, collaboratorResponses);
                            });
                        }));
    }

    // Same as create() - owner is always the caller here (requireOwner enforces it), so ownerUsername is left null rather than paying a shieldwall round-trip for a name the caller already knows
    public Mono<PlaylistResponse> update(UUID playlistId, UUID userId, UpdatePlaylistRequest request) {
        return requireOwner(playlistId, userId)
                .map(playlist -> {
                    playlist.setName(request.name());
                    playlist.setShared(request.shared());
                    return playlist;
                })
                .flatMap(playlistRepository::save)
                .flatMap(saved -> playlistTrackRepository.countByPlaylistId(playlistId)
                        .map(count -> new PlaylistResponse(saved.getId(), saved.getOwnerId(), null, saved.getName(), saved.isShared(), saved.getCreatedAt(), count)));
    }

    public Mono<Void> delete(UUID playlistId, UUID userId) {
        return requireOwner(playlistId, userId)
                .flatMap(playlistRepository::delete);
    }

    public Mono<Void> addTrack(UUID playlistId, UUID userId, AddTrackRequest request) {
        return requireAccess(playlistId, userId)
                .then(playlistTrackRepository.existsByPlaylistIdAndTrackId(playlistId, request.trackId()))
                .flatMap(exists -> exists
                        ? Mono.empty()
                        : playlistTrackRepository.nextPosition(playlistId)
                                .flatMap(position -> playlistTrackRepository.insert(playlistId, request.trackId(), position)));
    }

    public Mono<Void> removeTrack(UUID playlistId, UUID userId, UUID trackId) {
        return requireAccess(playlistId, userId)
                .then(playlistTrackRepository.deleteByPlaylistIdAndTrackId(playlistId, trackId));
    }

    // Full-list replace, not a partial move, client sends the whole new order, must be exactly the playlist's current track set (400 otherwise), so a caller can't smuggle in a track it never separately added via addTrack's own access check
    public Mono<Void> reorder(UUID playlistId, UUID userId, ReorderPlaylistRequest request) {
        return requireAccess(playlistId, userId)
                .then(playlistTrackRepository.findByPlaylistIdOrderByPosition(playlistId)
                        .map(PlaylistTrack::getTrackId)
                        .collectList())
                .flatMap(currentIds -> {
                    List<UUID> requestedIds = request.trackIds();
                    if (currentIds.size() != requestedIds.size() || !Set.copyOf(currentIds).equals(Set.copyOf(requestedIds))) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Reorder must include exactly the playlist's current tracks, no additions or drops"));
                    }
                    // concatMap, not flatMap - concurrent batched R2DBC/Postgres inserts have a documented bug (see GenreTagService) where parameter binds get tangled; same fix applied here defensively for the per-track UPDATEs
                    return Flux.fromIterable(requestedIds)
                            .index()
                            .concatMap(indexed -> playlistTrackRepository.updatePosition(playlistId, indexed.getT2(), indexed.getT1().intValue()))
                            .then();
                });
    }

    public Mono<Void> addCollaborator(UUID playlistId, UUID userId, AddCollaboratorRequest request) {
        return requireOwner(playlistId, userId)
                .flatMap(playlist -> playlist.isShared()
                        ? playlistCollaboratorRepository.upsert(playlistId, request.userId(), Instant.now())
                        : Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Playlist must be shared (shared=true) before adding collaborators")));
    }

    // Owner can remove anyone; a collaborator can only remove themselves ("leave"), anyone else is forbidden
    public Mono<Void> removeCollaborator(UUID playlistId, UUID userId, UUID targetUserId) {
        return findPlaylistOr404(playlistId)
                .flatMap(playlist -> {
                    boolean isOwner = playlist.getOwnerId().equals(userId);
                    if (isOwner || userId.equals(targetUserId)) {
                        return playlistCollaboratorRepository.deleteByPlaylistIdAndUserId(playlistId, targetUserId);
                    }
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can remove another collaborator"));
                });
    }

    public Flux<CollaboratorResponse> listCollaborators(UUID playlistId, UUID userId) {
        return requireAccess(playlistId, userId)
                .flatMapMany(playlist -> playlistCollaboratorRepository.findByPlaylistId(playlistId).collectList())
                .flatMap(collaborators -> {
                    List<UUID> userIds = collaborators.stream().map(PlaylistCollaborator::getUserId).collect(Collectors.toList());
                    return shieldwallUserClient.resolveUsernames(userIds)
                            .flatMapMany(usernames -> Flux.fromIterable(collaborators)
                                    .map(c -> CollaboratorResponse.from(c.getUserId(), c.getAddedAt(), usernames.get(c.getUserId()))));
                });
    }

    private Mono<Playlist> findPlaylistOr404(UUID playlistId) {
        return playlistRepository.findById(playlistId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found")));
    }

    private Mono<Playlist> requireOwner(UUID playlistId, UUID userId) {
        return findPlaylistOr404(playlistId)
                .flatMap(playlist -> playlist.getOwnerId().equals(userId)
                        ? Mono.just(playlist)
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your playlist")));
    }

    private Mono<Playlist> requireAccess(UUID playlistId, UUID userId) {
        return findPlaylistOr404(playlistId)
                .flatMap(playlist -> {
                    if (playlist.getOwnerId().equals(userId)) {
                        return Mono.just(playlist);
                    }
                    return playlistCollaboratorRepository.existsByPlaylistIdAndUserId(playlistId, userId)
                            .flatMap(isCollaborator -> isCollaborator
                                    ? Mono.just(playlist)
                                    : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to this playlist")));
                });
    }
}
