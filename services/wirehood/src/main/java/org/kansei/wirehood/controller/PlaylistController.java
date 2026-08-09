package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.AddCollaboratorRequest;
import org.kansei.wirehood.dto.AddTrackRequest;
import org.kansei.wirehood.dto.CollaboratorResponse;
import org.kansei.wirehood.dto.CreatePlaylistRequest;
import org.kansei.wirehood.dto.PlaylistDetailResponse;
import org.kansei.wirehood.dto.PlaylistResponse;
import org.kansei.wirehood.dto.ReorderPlaylistRequest;
import org.kansei.wirehood.dto.UpdatePlaylistRequest;
import org.kansei.wirehood.service.PlaylistService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

// Playlists are owner-scoped (private to owner + collaborators), unlike tracks/comments/genre-tags which are global every method here goes through PlaylistService's requireOwner/requireAccess check before touching data
@RestController
@RequestMapping("/wirehood/playlists")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @PostMapping
    public Mono<PlaylistResponse> create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody CreatePlaylistRequest request
    ) {
        return playlistService.create(userId, request);
    }

    @GetMapping("/mine")
    public Flux<PlaylistResponse> listAccessiblePlaylists(@RequestHeader("X-User-Id") UUID userId) {
        return playlistService.listAccessiblePlaylists(userId);
    }

    @GetMapping("/{playlistId}")
    public Mono<PlaylistDetailResponse> getPlaylistDetail(
            @PathVariable UUID playlistId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return playlistService.getPlaylistDetail(playlistId, userId);
    }

    @PatchMapping("/{playlistId}")
    public Mono<PlaylistResponse> update(
            @PathVariable UUID playlistId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody UpdatePlaylistRequest request
    ) {
        return playlistService.update(playlistId, userId, request);
    }

    @DeleteMapping("/{playlistId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID playlistId, @RequestHeader("X-User-Id") UUID userId) {
        return playlistService.delete(playlistId, userId);
    }

    @PostMapping("/{playlistId}/tracks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> addTrack(
            @PathVariable UUID playlistId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody AddTrackRequest request
    ) {
        return playlistService.addTrack(playlistId, userId, request);
    }

    @DeleteMapping("/{playlistId}/tracks/{trackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeTrack(
            @PathVariable UUID playlistId,
            @PathVariable UUID trackId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return playlistService.removeTrack(playlistId, userId, trackId);
    }

    @PutMapping("/{playlistId}/tracks/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reorder(
            @PathVariable UUID playlistId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody ReorderPlaylistRequest request
    ) {
        return playlistService.reorder(playlistId, userId, request);
    }

    @GetMapping("/{playlistId}/collaborators")
    public Flux<CollaboratorResponse> listCollaborators(
            @PathVariable UUID playlistId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return playlistService.listCollaborators(playlistId, userId);
    }

    @PostMapping("/{playlistId}/collaborators")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> addCollaborator(
            @PathVariable UUID playlistId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody AddCollaboratorRequest request
    ) {
        return playlistService.addCollaborator(playlistId, userId, request);
    }

    @DeleteMapping("/{playlistId}/collaborators/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeCollaborator(
            @PathVariable UUID playlistId,
            @PathVariable UUID targetUserId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return playlistService.removeCollaborator(playlistId, userId, targetUserId);
    }
}
