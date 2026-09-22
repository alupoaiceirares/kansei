package org.kansei.tailwind.stats;

import java.time.LocalDate;
import java.util.List;

/**
 * What the travel profile resolvers return. Every record maps one to one onto a type in the GraphQL schema.
 */
public final class StatsModels {

    private StatsModels() {
    }

    /**
     * Both ends optional and inclusive, on the flight's own local departure date.
     */
    public record Period(LocalDate from, LocalDate to) {
        public static final Period ALL_TIME = new Period(null, null);
    }

    public record TravelStats(
            int flightCount,
            double distanceKm,
            int timeInAirMinutes,
            int flightsWithDuration,
            int countryCount,
            int airportCount,
            int airlineCount,
            int aircraftFamilyCount,
            int aircraftTypeCount,
            Double longestFlightKm,
            Double averageFlightKm,
            int cargoFlightCount
    ) {
        public static final TravelStats EMPTY = new TravelStats(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, 0);
    }

    public record VisitedCountries(List<CountryVisit> visited, List<CountryVisit> passedThrough) {
    }

    public record CountryVisit(String code, String name, String continent, int visitCount, LocalDate firstVisit, LocalDate lastVisit) {
    }

    public record AirportVisit(Long id, String icao, String iata, String name, String city, String countryCode,
                               double latitude, double longitude, int timesUsed, int departures, int arrivals,
                               LocalDate firstVisit, LocalDate lastVisit) {
    }

    public record AircraftFamilyCollection(String family, int flightCount, double distanceKm, LocalDate firstFlight,
                                           LocalDate lastFlight, String photoUrl, List<AircraftVariantCount> variants) {
    }

    public record AircraftVariantCount(Long aircraftTypeId, String icaoCode, String name, String manufacturer, String bodyType,
                                       int flightCount, LocalDate firstFlight, LocalDate lastFlight, String photoUrl) {
    }

    public record AirlineCount(Long id, String icao, String iata, String name, int flightCount, double distanceKm) {
    }

    public record TravelRecords(FlightRecord longestFlight, FlightRecord shortestFlight, FlightRecord firstFlight,
                                JourneyRecord longestJourney, RouteRecord mostFlownRoute, NamedCount mostFlownAircraftFamily,
                                NamedCount mostFlownAirline, PeriodCount busiestMonth, PeriodCount biggestYear,
                                FurthestPoint furthestPoint, LongestGap longestGap, JourneyAircraftRecord mostAircraftInAJourney,
                                CabinRecord highestCabin) {
        public static final TravelRecords EMPTY =
                new TravelRecords(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * The airport furthest from home, where home is the airport used most often.
     */
    public record FurthestPoint(String iata, String name, String city, String countryCode, double distanceFromHomeKm,
                                String homeIata, LocalDate date) {
    }

    /**
     * The longest stretch with no flying, between two flights that are actually in the log.
     */
    public record LongestGap(int days, LocalDate from, LocalDate to) {
    }

    public record JourneyAircraftRecord(Long journeyId, String title, int aircraftCount, int flightCount) {
    }

    public record CabinRecord(String cabinClass, int flightCount, LocalDate firstFlight) {
    }

    /**
     * Counts of what the user recorded per flight, left out entirely when nothing was filled in.
     */
    public record TravelBreakdowns(List<NamedCount> cabinClasses, List<NamedCount> seatPositions, List<NamedCount> reasons,
                                   List<PeriodCount> flightsPerYear, int flightsWithCabin, int flightsWithReason,
                                   int flightsWithSeatPosition) {
        public static final TravelBreakdowns EMPTY =
                new TravelBreakdowns(List.of(), List.of(), List.of(), List.of(), 0, 0, 0);
    }

    /**
     * Every airliner family tailwind knows, with what the user has flown of it. flightCount 0 is a family they
     * have never been on, which is what the collection shows as locked.
     */
    public record AircraftFamilyOption(String family, String manufacturer, String bodyType, int variantCount,
                                       int flightCount, String photoUrl) {
    }

    public record FlightRecord(Long userFlightId, String flightNumber, LocalDate date, double distanceKm,
                               String departureIata, String arrivalIata, String airlineName, String aircraftName) {
    }

    public record JourneyRecord(Long journeyId, String title, int flightCount, double distanceKm, LocalDate startDate, LocalDate endDate) {
    }

    public record RouteRecord(String departureIata, String arrivalIata, String departureName, String arrivalName,
                              int flightCount, double distanceKm) {
    }

    public record NamedCount(String name, int count) {
    }

    public record PeriodCount(String label, int count, double distanceKm) {
    }
}
