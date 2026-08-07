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
 * id is DB-generated (gen_random_uuid() default), unlike TrackGenreTag's client-assigned composite key - so this can use plain ReactiveCrudRepository, no Persistable workaround needed
 */
@Table("track_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackComment {

    @Id
    private UUID id;

    private UUID trackId;

    private UUID userId;

    private UUID parentCommentId;

    private String body;

    private Instant createdAt;

    private Instant editedAt;

    private Instant deletedAt;
}
