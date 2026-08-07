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
 * Crowd-tagging vote, one row per (track, genre, user) - true composite PK (track_id, genre_id, user_id) in the schema, unlike user_library/download_requests which use a surrogate id workaround.
 * No single @Id column here on purpose - reads/writes go through TrackGenreTagRepository's custom @Query methods (raw upsert), not ReactiveCrudRepository.save(),
 * since R2DBC's isNew() null-check would misfire on an all-client-assigned composite key the same way it did for WirehoodUser before that got the Persistable fix
 */
@Table("track_genre_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackGenreTag {

    private UUID trackId;

    private UUID genreId;

    private UUID userId;

    private Instant taggedAt;
}
