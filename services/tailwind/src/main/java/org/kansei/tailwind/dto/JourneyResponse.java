package org.kansei.tailwind.dto;

import java.time.Instant;
import java.util.List;

/**
 * title is the user's own title, or "FRA - JFK" built from the first departure and last arrival when unset.
 */
public record JourneyResponse(Long id, String title, boolean titleIsCustom, String notes, Instant createdAt, List<UserFlightResponse> flights) {
}
