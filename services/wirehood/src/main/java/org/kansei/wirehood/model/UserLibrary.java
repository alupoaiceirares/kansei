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
 * "My saved tracks" - one row per (user, track) pair. Surrogate id + UNIQUE(user_id, track_id) rather than a true composite PK
 */
@Table("user_library")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLibrary {

    @Id
    private UUID id;

    private UUID userId;

    private UUID trackId;

    private Instant addedAt;
}
