package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.TrackComment;

import java.time.Instant;
import java.util.UUID;

/**
 * body is scrubbed (null) when deleted=true, rendered as "[deleted]" client-side - the DB keeps the real body around (soft delete), this is a read-time formatting decision, not a stored string.
 * username comes from ShieldwallUserClient (batch id-to-username lookup) - if that call failed or the id wasn't in the result, this is UNKNOWN_USERNAME, never the raw userId (showing the id instead would defeat the point of doing this lookup at all)
 */
public record CommentResponse(
        UUID id,
        UUID trackId,
        UUID userId,
        String username,
        UUID parentCommentId,
        String body,
        Instant createdAt,
        Instant editedAt,
        boolean deleted
) {
    private static final String UNKNOWN_USERNAME = "Unknown user";

    public static CommentResponse from(TrackComment comment, String username) {
        boolean deleted = comment.getDeletedAt() != null;
        return new CommentResponse(
                comment.getId(),
                comment.getTrackId(),
                comment.getUserId(),
                username == null ? UNKNOWN_USERNAME : username,
                comment.getParentCommentId(),
                deleted ? null : comment.getBody(),
                comment.getCreatedAt(),
                comment.getEditedAt(),
                deleted
        );
    }
}
