package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.Airline;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AirlineRepository extends JpaRepository<Airline, Long> {

    // Exact code hits first, then active airlines, then by name. contains is already lowercased and LIKE-escaped
    @Query("""
            select a from Airline a
            where upper(a.iata) = :code or upper(a.icao) = :code
               or lower(a.name) like :contains escape '\\'
            order by case when upper(a.iata) = :code or upper(a.icao) = :code then 0 else 1 end,
                     a.active desc, a.name
            """)
    List<Airline> search(@Param("code") String code, @Param("contains") String contains, Pageable pageable);

    boolean existsByIcao(String icao);

    Optional<Airline> findByIcao(String icao);

    List<Airline> findByIataAndActiveTrue(String iata);
}
