package org.kansei.tailwind.dto;

import java.time.LocalDate;

/**
 * A provider model string no aircraft type was resolved for. family is what could still be worked out, may be null.
 */
public record UnmappedAircraftString(String modelString, String family, long flightCount, LocalDate lastSeen) {
}
