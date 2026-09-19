package org.kansei.tailwind.dto;

import org.kansei.tailwind.model.Airline;

public record AirlineResponse(
        Long id,
        String icao,
        String iata,
        String name,
        String countryCode,
        boolean active
) {

    public static AirlineResponse of(Airline a) {
        return new AirlineResponse(a.getId(), a.getIcao(), a.getIata(), a.getName(), a.getCountryCode(), a.isActive());
    }
}
