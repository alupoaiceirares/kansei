package org.kansei.tailwind.flightdata;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One flight leg as the flight data provider describes it, already mapped away from the provider's JSON shape.
 * flightDate is the local departure date and may be null when the provider sent no departure time.
 * rawJson is the untouched provider element, kept on the stored flight.
 */
public record ExternalFlight(
        String flightNumber,
        LocalDate flightDate,
        String status,
        boolean cargo,
        Airline airline,
        Aircraft aircraft,
        Airport departure,
        Airport arrival,
        Instant departureScheduled,
        Instant departureRevised,
        Instant departureActual,
        Instant arrivalScheduled,
        Instant arrivalRevised,
        Instant arrivalActual,
        Instant lastUpdated,
        String rawJson
) {

    public record Airline(String name, String iata, String icao) {
    }

    public record Aircraft(String model, String registration, String modeS) {
    }

    public record Airport(String icao, String iata, String name, String city, String countryCode, String timeZone,
                          Double latitude, Double longitude) {
    }
}
