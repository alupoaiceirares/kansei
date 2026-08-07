package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.CommentResponse;
import org.kansei.wirehood.dto.EditCommentRequest;
import org.kansei.wirehood.service.CommentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

// Item-level operations address a comment by its own id directly, unlike create/list which are nested under /tracks/{trackId} since they need that context
@RestController
@RequestMapping("/wirehood/comments/{commentId}")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PatchMapping
    public Mono<CommentResponse> edit(
            @PathVariable UUID commentId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody EditCommentRequest request
    ) {
        return commentService.edit(commentId, userId, request);
    }

    @DeleteMapping
    public Mono<Void> delete(@PathVariable UUID commentId, @RequestHeader("X-User-Id") UUID userId) {
        return commentService.softDelete(commentId, userId);
    }
}
