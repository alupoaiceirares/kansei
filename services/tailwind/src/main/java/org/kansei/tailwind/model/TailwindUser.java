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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    // Set by an admin disable, cleared on reinstatement
    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "default_visibility", nullable = false)
    private Visibility defaultVisibility = Visibility.FRIENDS;

    // Frontend display choices, stored as handed over and never interpreted by the backend
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_preferences", columnDefinition = "jsonb")
    private String uiPreferences;
}
