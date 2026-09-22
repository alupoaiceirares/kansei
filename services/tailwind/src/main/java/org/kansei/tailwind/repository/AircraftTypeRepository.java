package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.model.BodyType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AircraftTypeRepository extends JpaRepository<AircraftType, Long> {

    // Exact designator first, then by name. contains is already lowercased and LIKE-escaped
    @Query("""
            select t from AircraftType t
            where upper(t.icaoCode) = :code
               or lower(t.name) like :contains escape '\\'
               or lower(t.family) like :contains escape '\\'
            order by case when upper(t.icaoCode) = :code then 0 else 1 end, t.name
            """)
    List<AircraftType> search(@Param("code") String code, @Param("contains") String contains, Pageable pageable);

    boolean existsByIcaoCode(String icaoCode);

    boolean existsByFamily(String family);

    Optional<AircraftType> findByIcaoCode(String icaoCode);

    @Query("select distinct t.manufacturer from AircraftType t")
    List<String> findDistinctManufacturers();

    @Query("select distinct t.family from AircraftType t")
    List<String> findDistinctFamilies();

    /**
     * The airliner families only: a family counts when at least one of its types is a wide or narrow body.
     * The seed also holds military and general aviation types, which have no place in a collection.
     */
    @Query("""
            select t.family, min(t.manufacturer), min(t.bodyType), count(t)
            from AircraftType t
            where t.family is not null
              and exists (select 1 from AircraftType w where w.family = t.family and w.bodyType in :bodyTypes)
            group by t.family
            order by t.family
            """)
    List<Object[]> findAirlinerFamilies(@Param("bodyTypes") List<BodyType> bodyTypes);
}
