package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.Journey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JourneyRepository extends JpaRepository<Journey, Long> {

    List<Journey> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    Optional<Journey> findByIdAndUserId(Long id, UUID userId);

    @Modifying
    @Query("delete from Journey j where j.userId in :userIds")
    int deleteByUserIds(@Param("userIds") Collection<UUID> userIds);
}
