package org.kansei.wirehood.service;

import org.kansei.wirehood.client.ShieldwallUserClient;
import org.kansei.wirehood.dto.CommentResponse;
import org.kansei.wirehood.dto.CreateCommentRequest;
import org.kansei.wirehood.dto.CursorPageResponse;
import org.kansei.wirehood.dto.EditCommentRequest;
import org.kansei.wirehood.model.TrackComment;
import org.kansei.wirehood.repository.TrackCommentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private final TrackCommentRepository trackCommentRepository;
    private final ShieldwallUserClient shieldwallUserClient;
    private final AdminAuthService adminAuthService;

    public CommentService(
            TrackCommentRepository trackCommentRepository,
            ShieldwallUserClient shieldwallUserClient,
            AdminAuthService adminAuthService
    ) {
        this.trackCommentRepository = trackCommentRepository;
        this.shieldwallUserClient = shieldwallUserClient;
        this.adminAuthService = adminAuthService;
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

    // One batch username lookup for the whole page, not one per comment
    public Mono<CursorPageResponse<CommentResponse>> listForTrack(UUID trackId, String cursor, int size) {
        int limit = clampSize(size);
        Flux<TrackComment> fetch = cursor == null
                ? trackCommentRepository.findFirstPage(trackId, limit + 1)
                : fetchAfterCursor(trackId, cursor, limit + 1);

        return Mono.zip(fetch.collectList(), trackCommentRepository.countByTrackId(trackId))
                .flatMap(resolved -> {
                    List<TrackComment> fetched = resolved.getT1();
                    long total = resolved.getT2();
                    boolean hasMore = fetched.size() > limit;
                    List<TrackComment> pageItems = hasMore ? fetched.subList(0, limit) : fetched;
                    String nextCursor = hasMore ? encodeCursor(pageItems.get(pageItems.size() - 1)) : null;

                    List<UUID> userIds = pageItems.stream().map(TrackComment::getUserId).collect(Collectors.toList());
                    return shieldwallUserClient.resolveUsernames(userIds)
                            .map(usernames -> pageItems.stream()
                                    .map(comment -> CommentResponse.from(comment, usernames.get(comment.getUserId())))
                                    .collect(Collectors.toList()))
                            .map(items -> CursorPageResponse.of(items, nextCursor, total));
                });
    }

    private Flux<TrackComment> fetchAfterCursor(UUID trackId, String cursor, int limit) {
        CommentCursor decoded = decodeCursor(cursor);
        return trackCommentRepository.findAfterCursor(trackId, decoded.createdAt(), decoded.id(), limit);
    }

    private String encodeCursor(TrackComment comment) {
        String raw = comment.getCreatedAt().toString() + "|" + comment.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private CommentCursor decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            return new CommentCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    private record CommentCursor(Instant createdAt, UUID id) {
    }

    // 1-100, defaulting downward rather than erroring
    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
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

    // Own comment OR an admin, editing stays owner-only, only delete is admin-gated
    public Mono<Void> softDelete(UUID commentId, UUID userId) {
        return findOwnedOrAdmin(commentId, userId)
                .map(comment -> {
                    comment.setDeletedAt(Instant.now());
                    return comment;
                })
                .flatMap(trackCommentRepository::save)
                .then();
    }

    // Edit restricted to the comment's own author - 404 if the comment doesn't exist, 403 if it exists but belongs to someone else
    private Mono<TrackComment> findOwned(UUID commentId, UUID userId) {
        return trackCommentRepository.findById(commentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found")))
                .flatMap(comment -> comment.getUserId().equals(userId)
                        ? Mono.just(comment)
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your comment")));
    }

    private Mono<TrackComment> findOwnedOrAdmin(UUID commentId, UUID userId) {
        return trackCommentRepository.findById(commentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found")))
                .flatMap(comment -> comment.getUserId().equals(userId)
                        ? Mono.just(comment)
                        : adminAuthService.requireAdmin(userId).thenReturn(comment));
    }
}
