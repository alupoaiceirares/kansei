package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.YearlyRecap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface YearlyRecapRepository extends JpaRepository<YearlyRecap, Long> {

    Optional<YearlyRecap> findByUserIdAndYear(UUID userId, int year);

    List<YearlyRecap> findByYearAndEmailedAtIsNull(int year);

    @Modifying
    @Query("delete from YearlyRecap r where r.userId in :userIds")
    int deleteByUserIds(@Param("userIds") Collection<UUID> userIds);
}
