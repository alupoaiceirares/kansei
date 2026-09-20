package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.AircraftPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AircraftPhotoRepository extends JpaRepository<AircraftPhoto, Long> {

    Optional<AircraftPhoto> findByAircraftTypeId(Long aircraftTypeId);

    // Oldest photo first, so a family shows the same photo until an admin replaces it
    @Query("""
            select p from AircraftPhoto p, AircraftType t
            where p.aircraftTypeId = t.id and t.family = :family
            order by p.createdAt, p.id
            """)
    List<AircraftPhoto> findByFamily(@Param("family") String family);
}
