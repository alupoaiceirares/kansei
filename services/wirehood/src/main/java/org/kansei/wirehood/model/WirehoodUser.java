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
 * Marks a shieldwall user as opted into wirehood - inserted when the frontend's "would you like to use wirehood?" popup is confirmed, not automatically on login. user_id IS the PK
 */
@Table("wirehood_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WirehoodUser {

    @Id
    private UUID userId;

    @Builder.Default
    private WirehoodRole role = WirehoodRole.USER;

    private Instant joinedAt;
}
