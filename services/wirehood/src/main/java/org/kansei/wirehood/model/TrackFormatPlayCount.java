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
 * One row per (user, track_format) pair, incremented each time that user actually pulls the file's byte, same no-@Id/bare-Repository shape as TrackFormatFavorite
 */
@Table("track_format_play_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackFormatPlayCount {

    private UUID userId;

    private UUID trackFormatId;

    private Integer playCount;

    private Instant lastPlayedAt;
}
