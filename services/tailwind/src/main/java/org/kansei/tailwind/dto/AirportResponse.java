package org.kansei.tailwind.dto;

import org.kansei.tailwind.model.Airport;

public record AirportResponse(
        Long id,
        String icao,
        String iata,
        String name,
        String city,
        String countryCode,
        double latitude,
        double longitude,
        String timeZone,
        String airportType
) {

    public static AirportResponse of(Airport a) {
        return new AirportResponse(a.getId(), a.getIcao(), a.getIata(), a.getName(), a.getCity(), a.getCountryCode(),
                a.getLatitude(), a.getLongitude(), a.getTimeZone(), a.getAirportType());
    }
}
