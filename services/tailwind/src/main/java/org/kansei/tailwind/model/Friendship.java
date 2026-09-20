package org.kansei.tailwind.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per pair of users, whatever the direction. requestedBy is who asked, which is what accept and
 * decline check, and is independent of the id ordering.
 */
@Entity
@Table(name = "friendships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship {

    @EmbeddedId
    private FriendshipId id;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public boolean accepted() {
        return status == FriendshipStatus.ACCEPTED;
    }

    public UUID otherThan(UUID userId) {
        return id.other(userId);
    }
}
