package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.Airport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, Long> {

    // Exact code hits first, then bigger airports, then by name. prefix and contains are already lowercased and LIKE-escaped
    @Query("""
            select a from Airport a
            where upper(a.iata) = :code or upper(a.icao) = :code
               or lower(a.city) like :prefix escape '\\'
               or lower(a.name) like :contains escape '\\'
            order by case when upper(a.iata) = :code or upper(a.icao) = :code then 0 else 1 end,
                     case a.airportType when 'large_airport' then 0 when 'medium_airport' then 1 else 2 end,
                     a.name
            """)
    List<Airport> search(@Param("code") String code, @Param("prefix") String prefix,
                         @Param("contains") String contains, Pageable pageable);

    boolean existsByIcao(String icao);

    boolean existsByIata(String iata);

    Optional<Airport> findByIcao(String icao);

    Optional<Airport> findByIata(String iata);
}
