package org.kansei.wirehood.service;

import org.kansei.wirehood.client.ShieldwallUserClient;
import org.kansei.wirehood.dto.CommentResponse;
import org.kansei.wirehood.dto.CreateCommentRequest;
import org.kansei.wirehood.dto.EditCommentRequest;
import org.kansei.wirehood.model.TrackComment;
import org.kansei.wirehood.repository.TrackCommentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private final TrackCommentRepository trackCommentRepository;
    private final ShieldwallUserClient shieldwallUserClient;

    public CommentService(TrackCommentRepository trackCommentRepository, ShieldwallUserClient shieldwallUserClient) {
        this.trackCommentRepository = trackCommentRepository;
        this.shieldwallUserClient = shieldwallUserClient;
    }

    public Mono<CommentResponse> post(UUID trackId, UUID userId, CreateCommentRequest request) {
        TrackComment comment = TrackComment.builder()
                .trackId(trackId)
                .userId(userId)
                .parentCommentId(request.parentCommentId())
                .body(request.body())
                .createdAt(Instant.now())
                .build();
        return trackCommentRepository.save(comment)
                .flatMap(saved -> shieldwallUserClient.resolveUsernames(List.of(userId))
                        .map(usernames -> CommentResponse.from(saved, usernames.get(userId))));
    }

    // One batch username lookup for the whole list, not one per comment
    public Flux<CommentResponse> listForTrack(UUID trackId) {
        return trackCommentRepository.findByTrackIdOrderByCreatedAtAsc(trackId)
                .collectList()
                .flatMapMany(comments -> {
                    List<UUID> userIds = comments.stream().map(TrackComment::getUserId).collect(Collectors.toList());
                    return shieldwallUserClient.resolveUsernames(userIds)
                            .flatMapMany(usernames -> Flux.fromIterable(comments)
                                    .map(comment -> CommentResponse.from(comment, usernames.get(comment.getUserId()))));
                });
    }

    public Mono<CommentResponse> edit(UUID commentId, UUID userId, EditCommentRequest request) {
        return findOwned(commentId, userId)
                .map(comment -> {
                    comment.setBody(request.body());
                    comment.setEditedAt(Instant.now());
                    return comment;
                })
                .flatMap(trackCommentRepository::save)
                .flatMap(saved -> shieldwallUserClient.resolveUsernames(List.of(userId))
                        .map(usernames -> CommentResponse.from(saved, usernames.get(userId))));
    }

    public Mono<Void> softDelete(UUID commentId, UUID userId) {
        return findOwned(commentId, userId)
                .map(comment -> {
                    comment.setDeletedAt(Instant.now());
                    return comment;
                })
                .flatMap(trackCommentRepository::save)
                .then();
    }

    // Edit/delete restricted to the comment's own author - 404 if the comment doesn't exist, 403 if it exists but belongs to someone else
    private Mono<TrackComment> findOwned(UUID commentId, UUID userId) {
        return trackCommentRepository.findById(commentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found")))
                .flatMap(comment -> comment.getUserId().equals(userId)
                        ? Mono.just(comment)
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your comment")));
    }
}
