package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.FlightJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FlightJoinRequestRepository extends JpaRepository<FlightJoinRequest, Long> {

    String DETAILS = """
            select r from FlightJoinRequest r
            join fetch r.userFlight uf
            join fetch uf.flight f
            join fetch f.airline
            join fetch f.departureAirport
            join fetch f.arrivalAirport
            left join fetch f.aircraftType
            """;

    // Requests waiting on this user's own entries
    @Query(DETAILS + " where uf.userId = :userId order by r.requestedAt desc")
    List<FlightJoinRequest> findIncoming(@Param("userId") UUID userId);

    @Query(DETAILS + " where r.requesterId = :userId order by r.requestedAt desc")
    List<FlightJoinRequest> findOutgoing(@Param("userId") UUID userId);

    boolean existsByRequesterIdAndUserFlight_Id(UUID requesterId, Long userFlightId);

    @Modifying
    @Query("delete from FlightJoinRequest r where r.requesterId in :userIds")
    int deleteByRequesterIds(@Param("userIds") Collection<UUID> userIds);
}
