package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.Friendship;
import org.kansei.tailwind.model.FriendshipId;
import org.kansei.tailwind.model.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {

    @Query("select f from Friendship f where (f.id.userIdA = :userId or f.id.userIdB = :userId) and f.status = :status")
    List<Friendship> findByUserAndStatus(@Param("userId") UUID userId, @Param("status") FriendshipStatus status);

    @Query("select f from Friendship f where f.id.userIdA = :userId or f.id.userIdB = :userId")
    List<Friendship> findByUser(@Param("userId") UUID userId);

    @Query("""
            select count(f) > 0 from Friendship f
            where f.status = org.kansei.tailwind.model.FriendshipStatus.ACCEPTED
              and ((f.id.userIdA = :one and f.id.userIdB = :other) or (f.id.userIdA = :other and f.id.userIdB = :one))
            """)
    boolean areFriends(@Param("one") UUID one, @Param("other") UUID other);

    @Modifying
    @Query("delete from Friendship f where f.id.userIdA in :userIds or f.id.userIdB in :userIds")
    int deleteByUserIds(@Param("userIds") Collection<UUID> userIds);
}
