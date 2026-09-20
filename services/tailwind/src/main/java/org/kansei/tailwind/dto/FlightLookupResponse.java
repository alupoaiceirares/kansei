package org.kansei.tailwind.dto;

import java.util.List;

/**
 * Result of a flight number lookup, one entry per leg. Nothing is added to the user's log yet, the confirm
 * call does that. cargo entries need an explicit confirmation there.
 */
public record FlightLookupResponse(List<Entry> flights) {

    public record Entry(FlightResponse flight, boolean alreadyInLog) {
    }
}
