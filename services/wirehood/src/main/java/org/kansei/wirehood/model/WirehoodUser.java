package org.kansei.wirehood.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Marks a shieldwall user as opted into wirehood - inserted when the frontend's "would you like to use wirehood?" popup is confirmed, not automatically on login. user_id IS the PK
 * Implements Persistable because userId is client-assigned (not DB-generated) - without this, R2DBC's null-check-based isNew() logic mistakes every save() for an UPDATE and silently no-ops on rows that don't exist yet
 */
@Table("wirehood_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WirehoodUser implements Persistable<UUID> {

    @Id
    private UUID userId;

    @Builder.Default
    private WirehoodRole role = WirehoodRole.USER;

    private Instant joinedAt;

    @Builder.Default
    private boolean enabled = true;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    @JsonIgnore
    public UUID getId() {
        return userId;
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return isNew;
    }
}
