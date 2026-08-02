package org.kansei.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String username;

    private String firstName;

    private String lastName;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // Login is rejected until the confirmation email link is clicked.
    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    /**
     * Password/Email change
     * Every JWT embeds the version it was issued under - JwtAuthenticationFilter rejects a token whose embedded version no longer matches this, forcing re-login after those changes.
     */
    @Column(nullable = false)
    @Builder.Default
    private int credentialsVersion = 0;

    /**
     * Set on deactivation, cleared on reactivation (login within the retention window).
     * Past the retention window, the purge job hard-deletes the row.
     */
    private Instant deactivatedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}