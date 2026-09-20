package org.kansei.tailwind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * The pair key, always with the smaller id first, so one pair can never exist as two rows.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FriendshipId implements Serializable {

    @Column(name = "user_id_a")
    private UUID userIdA;

    @Column(name = "user_id_b")
    private UUID userIdB;

    public static FriendshipId of(UUID one, UUID other) {
        return one.compareTo(other) <= 0 ? new FriendshipId(one, other) : new FriendshipId(other, one);
    }

    public UUID other(UUID userId) {
        return userId.equals(userIdA) ? userIdB : userIdA;
    }
}
