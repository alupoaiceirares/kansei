package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.UserFlight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserFlightRepository extends JpaRepository<UserFlight, Long> {

    String DETAILS = """
            select uf from UserFlight uf
            join fetch uf.flight f
            join fetch f.airline
            join fetch f.departureAirport
            join fetch f.arrivalAirport
            left join fetch f.aircraftType
            """;

    @Query(DETAILS + " where uf.userId = :userId order by f.flightDate, f.departureScheduledUtc nulls last, uf.id")
    List<UserFlight> findDetailedByUserId(@Param("userId") UUID userId);

    @Query(DETAILS + " where uf.journeyId = :journeyId order by f.flightDate, f.departureScheduledUtc nulls last, uf.id")
    List<UserFlight> findDetailedByJourneyId(@Param("journeyId") Long journeyId);

    @Query(DETAILS + " where uf.id = :id and uf.userId = :userId")
    Optional<UserFlight> findDetailedByIdAndUserId(@Param("id") Long id, @Param("userId") UUID userId);

    Optional<UserFlight> findByIdAndUserId(Long id, UUID userId);

    boolean existsByUserIdAndFlightId(UUID userId, Long flightId);

    long countByJourneyId(Long journeyId);

    List<UserFlight> findByJourneyId(Long journeyId);

    @Query("select uf.flight.id from UserFlight uf where uf.userId in :userIds")
    List<Long> findFlightIdsByUserIds(@Param("userIds") Collection<UUID> userIds);

    @Query("select uf.flight.id from UserFlight uf where uf.journeyId = :journeyId")
    List<Long> findFlightIdsByJourneyId(@Param("journeyId") Long journeyId);

    @Modifying
    @Query("delete from UserFlight uf where uf.userId in :userIds")
    int deleteByUserIds(@Param("userIds") Collection<UUID> userIds);

    @Modifying
    @Query("delete from UserFlight uf where uf.journeyId = :journeyId")
    int deleteByJourneyId(@Param("journeyId") Long journeyId);
}
