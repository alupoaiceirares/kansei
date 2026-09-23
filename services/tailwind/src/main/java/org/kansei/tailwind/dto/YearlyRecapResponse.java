package org.kansei.tailwind.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One user's year in the air, owner only, private flights included. The page and the January email are built
 * from this same record. earthLaps and moonTrips compare the distance to the equator and to the Moon.
 */
public record YearlyRecapResponse(
        int year,
        int flightCount,
        double distanceKm,
        double earthLaps,
        double moonTrips,
        int timeInAirMinutes,
        int flightsWithDuration,
        int countryCount,
        List<NamedCode> newCountries,
        int airportCount,
        int airlineCount,
        List<String> newAircraftFamilies,
        List<Integer> monthCounts,
        Integer busiestMonth,
        Moment longestFlight,
        Moment firstFlight,
        NamedCount topAirline
) {

    public record NamedCode(String code, String name) {
    }

    public record NamedCount(String name, int count) {
    }

    /**
     * A single flight worth mentioning. aircraft is the type name, else the family, may be null.
     */
    public record Moment(Long userFlightId, String route, LocalDate date, double distanceKm, String aircraft) {
    }
}
