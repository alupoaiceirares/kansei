package org.kansei.wirehood.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (user, track_format) pair a user has favorited, true composite PK (user_id, track_format_id), same no-@Id/bare-Repository shape as TrackGenreTag since both key columns are always client/server-assigned
 */
@Table("track_format_favorites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackFormatFavorite {

    private UUID userId;

    private UUID trackFormatId;

    private Instant favoritedAt;
}
