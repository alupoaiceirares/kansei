package org.kansei.tailwind.dto;

import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.StopType;
import org.kansei.tailwind.model.TripReason;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.model.Visibility;

import java.time.Instant;

/**
 * stopType is what happens after this flight: the explicit choice when the user made one, else the
 * suggestion. Both are null for the last flight of a journey.
 */
public record UserFlightResponse(
        Long id,
        Long journeyId,
        FlightResponse flight,
        Visibility visibility,
        String seat,
        SeatPosition seatPosition,
        CabinClass cabinClass,
        TripReason reason,
        StopType stopType,
        StopType stopTypeSuggested,
        String notes
) {

    public static UserFlightResponse of(UserFlight uf, StopType suggested, Instant now) {
        StopType effective = suggested == null ? null : (uf.getStopType() != null ? uf.getStopType() : suggested);
        return new UserFlightResponse(uf.getId(), uf.getJourneyId(), FlightResponse.of(uf.getFlight(), now), uf.getVisibility(), uf.getSeat(),
                uf.getSeatPosition(), uf.getCabinClass(), uf.getReason(), effective, suggested, uf.getNotes());
    }
}
