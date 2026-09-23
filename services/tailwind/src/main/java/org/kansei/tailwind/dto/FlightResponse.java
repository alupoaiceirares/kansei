package org.kansei.tailwind.dto;

import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.model.Airline;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.model.Flight;
import org.kansei.tailwind.model.FlightSource;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A canonical flight as the confirm screen and the flight list show it. Times are UTC, the airport time zone
 * is included so the client can render local times. The raw provider payload is never exposed.
 */
public record FlightResponse(
        Long id,
        String flightNumber,
        LocalDate flightDate,
        FlightSource source,
        String status,
        boolean cargo,
        boolean upcoming,
        boolean awaitingRefresh,
        double distanceKm,
        AirlineRef airline,
        AirportRef departureAirport,
        AirportRef arrivalAirport,
        Instant departureScheduledUtc,
        Instant departureRevisedUtc,
        Instant departureActualUtc,
        Instant arrivalScheduledUtc,
        Instant arrivalRevisedUtc,
        Instant arrivalActualUtc,
        AircraftInfo aircraft
) {

    public record AirlineRef(Long id, String icao, String iata, String name) {
        static AirlineRef of(Airline a) {
            return new AirlineRef(a.getId(), a.getIcao(), a.getIata(), a.getName());
        }
    }

    public record AirportRef(Long id, String icao, String iata, String name, String city, String countryCode, String timeZone,
                             double latitude, double longitude) {
        static AirportRef of(Airport a) {
            return new AirportRef(a.getId(), a.getIcao(), a.getIata(), a.getName(), a.getCity(), a.getCountryCode(), a.getTimeZone(),
                    a.getLatitude(), a.getLongitude());
        }
    }

    /**
     * typeIcao and typeName are null when only the family (or nothing) is known.
     */
    public record AircraftInfo(String family, String typeIcao, String typeName, String modelRaw, String registration) {
    }

    public static FlightResponse of(Flight f, Instant now) {
        AircraftType type = f.getAircraftType();
        return new FlightResponse(f.getId(), f.getFlightNumber(), f.getFlightDate(), f.getSource(), f.getStatus(), f.isCargo(), f.isUpcoming(now),
                f.isAwaitingRefresh(),
                f.getDistanceKm(), AirlineRef.of(f.getAirline()), AirportRef.of(f.getDepartureAirport()), AirportRef.of(f.getArrivalAirport()),
                f.getDepartureScheduledUtc(), f.getDepartureRevisedUtc(), f.getDepartureActualUtc(),
                f.getArrivalScheduledUtc(), f.getArrivalRevisedUtc(), f.getArrivalActualUtc(),
                new AircraftInfo(f.getAircraftFamily(), type == null ? null : type.getIcaoCode(), type == null ? null : type.getName(),
                        f.getAircraftModelRaw(), f.getRegistration()));
    }
}
