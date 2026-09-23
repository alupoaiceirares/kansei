package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.CsvImport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CsvImportRepository extends JpaRepository<CsvImport, Long> {

    List<CsvImport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying
    @Query("delete from CsvImport c where c.targetUserId in :userIds")
    int deleteByTargetUserIds(@Param("userIds") Collection<UUID> userIds);
}
