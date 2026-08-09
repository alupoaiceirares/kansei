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
 * True composite PK (user_id_a, user_id_b), no single @Id column, userIdA/userIdB are order-independent so a pair can't exist as two rows in both directions
 */
@Table("friendships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship {

    private UUID userIdA;

    private UUID userIdB;

    private UUID requestedBy;

    @Builder.Default
    private FriendshipStatus status = FriendshipStatus.PENDING;

    private Instant requestedAt;

    private Instant respondedAt;
}
