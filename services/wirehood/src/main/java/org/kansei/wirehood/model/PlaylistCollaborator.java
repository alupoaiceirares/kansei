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
 * True composite PK (playlist_id, user_id) - same pattern as TrackGenreTag/PlaylistTrack, no single @Id column
 */
@Table("playlist_collaborators")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaylistCollaborator {

    private UUID playlistId;

    private UUID userId;

    private Instant addedAt;
}
