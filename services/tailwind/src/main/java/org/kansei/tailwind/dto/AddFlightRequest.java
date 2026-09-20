package org.kansei.tailwind.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.TripReason;
import org.kansei.tailwind.model.Visibility;

/**
 * Confirms a looked-up flight into the user's log. visibility defaults to the user's default, journeyId to a
 * new journey. confirmCargo must be true for a cargo flight.
 */
public record AddFlightRequest(
        @NotNull Long flightId,
        Visibility visibility,
        Long journeyId,
        Boolean confirmCargo,
        @Size(max = 8) String seat,
        SeatPosition seatPosition,
        CabinClass cabinClass,
        TripReason reason,
        @Size(max = 2000) String notes
) {
}
