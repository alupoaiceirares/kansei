package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.Friendship;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * No single @Id column on Friendship (composite PK user_id_a+user_id_b)
 */
public interface FriendshipRepository extends Repository<Friendship, Void> {

    @Query("INSERT INTO friendships (user_id_a, user_id_b, requested_by, status, requested_at) VALUES (:userIdA, :userIdB, :requestedBy, 'PENDING', :requestedAt)")
    Mono<Void> insertPending(UUID userIdA, UUID userIdB, UUID requestedBy, Instant requestedAt);

    @Query("SELECT * FROM friendships WHERE user_id_a = :userIdA AND user_id_b = :userIdB")
    Mono<Friendship> findByPair(UUID userIdA, UUID userIdB);

    @Query("UPDATE friendships SET status = 'ACCEPTED', responded_at = :respondedAt WHERE user_id_a = :userIdA AND user_id_b = :userIdB")
    Mono<Void> markAccepted(UUID userIdA, UUID userIdB, Instant respondedAt);

    @Query("DELETE FROM friendships WHERE user_id_a = :userIdA AND user_id_b = :userIdB")
    Mono<Void> deleteByPair(UUID userIdA, UUID userIdB);

    // Serves "my friends" (filter ACCEPTED), "my pending requests" (filter PENDING), and search-result annotation - all filtered in-memory by the caller, one query covers all three
    @Query("SELECT * FROM friendships WHERE user_id_a = :userId OR user_id_b = :userId")
    Flux<Friendship> findAllForUser(UUID userId);

    // Music profile's friendCount field - cheaper than findAllForUser + client-side filter/count, which fetches full rows just to discard most of them
    @Query("SELECT COUNT(*) FROM friendships WHERE (user_id_a = :userId OR user_id_b = :userId) AND status = 'ACCEPTED'")
    Mono<Long> countAcceptedForUser(UUID userId);
}
