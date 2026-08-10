package org.kansei.wirehood.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A crowd-submitted replacement thumbnail, pending admin review, filePath tracks wherever the file urrently lives, the pending-submissions subdirectory while PENDING/REJECTED, the main storage root once APPROVED
 */
@Table("track_thumbnail_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackThumbnailSubmission {

    @Id
    private UUID id;

    private UUID trackId;

    private UUID submittedBy;

    private String filePath;

    @Builder.Default
    private ThumbnailSubmissionStatus status = ThumbnailSubmissionStatus.PENDING;

    private Instant submittedAt;

    private Instant reviewedAt;

    private UUID reviewedBy;
}
