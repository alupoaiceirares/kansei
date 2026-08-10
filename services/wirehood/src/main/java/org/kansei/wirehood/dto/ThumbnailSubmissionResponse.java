package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.ThumbnailSubmissionStatus;
import org.kansei.wirehood.model.TrackThumbnailSubmission;

import java.time.Instant;
import java.util.UUID;

public record ThumbnailSubmissionResponse(
        UUID id,
        UUID trackId,
        UUID submittedBy,
        ThumbnailSubmissionStatus status,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedBy
) {
    public static ThumbnailSubmissionResponse from(TrackThumbnailSubmission submission) {
        return new ThumbnailSubmissionResponse(
                submission.getId(),
                submission.getTrackId(),
                submission.getSubmittedBy(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getReviewedAt(),
                submission.getReviewedBy()
        );
    }
}
