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
 * id is DB-generated (gen_random_uuid() default), like TrackComment - plain ReactiveCrudRepository, no Persistable workaround needed
 */
@Table("playlists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Playlist {

    @Id
    private UUID id;

    private UUID ownerId;

    private String name;

    @Builder.Default
    private boolean shared = false;

    private Instant createdAt;
}
