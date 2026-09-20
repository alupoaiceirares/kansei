package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.Flight;
import org.kansei.tailwind.model.FlightSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {

    String DETAILS = """
            select f from Flight f
            join fetch f.airline
            join fetch f.departureAirport
            join fetch f.arrivalAirport
            left join fetch f.aircraftType
            """;

    @Query(DETAILS + " where f.flightNumber = :number and f.flightDate = :date and f.source = :source order by f.departureScheduledUtc, f.id")
    List<Flight> findDetailed(@Param("number") String number, @Param("date") LocalDate date, @Param("source") FlightSource source);

    @Query(DETAILS + " where f.id = :id")
    Optional<Flight> findDetailedById(@Param("id") Long id);

    boolean existsByFlightNumberAndFlightDateAndDepartureAirportIdAndSource(String number, LocalDate date, Long departureAirportId, FlightSource source);

    @Modifying
    @Query("delete from Flight f where f.id in :ids and f.source = org.kansei.tailwind.model.FlightSource.MANUAL")
    int deleteManualByIds(@Param("ids") Collection<Long> ids);
}
