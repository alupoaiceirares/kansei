package org.kansei.tailwind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A shieldwall user who opted into tailwind, user_id is the shieldwall id and the PK, never a FK.
 * Created by the opt-in call, not automatically on login.
 */
@Entity
@Table(name = "tailwind_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TailwindUser {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "default_visibility", nullable = false)
    private Visibility defaultVisibility = Visibility.PUBLIC;
}
