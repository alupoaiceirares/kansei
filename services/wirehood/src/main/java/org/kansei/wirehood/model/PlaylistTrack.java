package org.kansei.wirehood.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * True composite PK (playlist_id, track_id)
 * No single @Id column, reads/writes go through PlaylistTrackRepository's custom @Query methods, not ReactiveCrudRepository.save()
 */
@Table("playlist_tracks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaylistTrack {

    private UUID playlistId;

    private UUID trackId;

    private int position;
}
