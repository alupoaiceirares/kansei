package org.kansei.tailwind.stats;

import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.StopType;
import org.kansei.tailwind.model.TripReason;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One flight of one user, flattened for the aggregations. Loaded once per profile query and reused by every
 * resolver in it, so the stats page does not run the same join six times.
 */
public record StatsFlight(
        Long userFlightId,
        Long journeyId,
        Long flightId,
        LocalDate date,
        String flightNumber,
        double distanceKm,
        boolean cargo,
        StopType stopType,
        StopType suggestedStopType,
        Instant departure,
        Instant arrival,
        Long airlineId,
        String airlineIcao,
        String airlineIata,
        String airlineName,
        Long departureAirportId,
        String departureIcao,
        String departureIata,
        String departureName,
        String departureCity,
        String departureCountry,
        double departureLatitude,
        double departureLongitude,
        Long arrivalAirportId,
        String arrivalIcao,
        String arrivalIata,
        String arrivalName,
        String arrivalCity,
        String arrivalCountry,
        double arrivalLatitude,
        double arrivalLongitude,
        Long aircraftTypeId,
        String aircraftIcaoCode,
        String aircraftName,
        String aircraftManufacturer,
        String aircraftBodyType,
        String aircraftFamily,
        CabinClass cabinClass,
        SeatPosition seatPosition,
        TripReason reason
) {

    /**
     * What the user chose, or what the journey shape suggests when they never touched it.
     */
    public StopType effectiveStopType() {
        return stopType != null ? stopType : suggestedStopType;
    }

    public Integer durationMinutes() {
        if (departure == null || arrival == null || !arrival.isAfter(departure)) {
            return null;
        }
        return (int) java.time.Duration.between(departure, arrival).toMinutes();
    }
}
