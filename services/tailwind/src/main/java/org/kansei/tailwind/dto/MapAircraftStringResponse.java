package org.kansei.tailwind.dto;

/**
 * flightsUpdated counts the stored flights that picked up the type straight away.
 */
public record MapAircraftStringResponse(String aliasKey, AircraftTypeResponse aircraftType, int flightsUpdated) {
}
