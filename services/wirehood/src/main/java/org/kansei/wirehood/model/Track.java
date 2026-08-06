package org.kansei.wirehood.model;

import io.r2dbc.postgresql.codec.Json;
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
 * Canonical row per YouTube video - shared by every user's library (platform-wide dedup), see
 * WIREHOOD_PLAN.md. Timestamps/id aren't set here (unlike shieldwall's JPA @PrePersist) - R2DBC
 * has no equivalent lifecycle callback, id is left null so Postgres generates it
 * (id column default gen_random_uuid()) and timestamps are the caller's responsibility for now.
 */
@Table("tracks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Track {

    @Id
    private UUID id;

    private String youtubeVideoId;

    private String title;

    private String artist;

    private String extraInfo;

    private Integer durationSeconds;

    private String thumbnailPath;

    @Builder.Default
    private TrackStatus status = TrackStatus.PENDING;

    @Builder.Default
    private boolean visible = true;

    private Json details;

    private Instant createdAt;

    private Instant updatedAt;
}
