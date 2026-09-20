package org.kansei.tailwind.dto;

import jakarta.validation.constraints.Size;
import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.StopType;
import org.kansei.tailwind.model.TripReason;
import org.kansei.tailwind.model.Visibility;

/**
 * Partial update, a null field is left as it is. journeyId moves the flight to another of the user's journeys.
 */
public record UpdateUserFlightRequest(
        Visibility visibility,
        Long journeyId,
        @Size(max = 8) String seat,
        SeatPosition seatPosition,
        CabinClass cabinClass,
        TripReason reason,
        StopType stopType,
        @Size(max = 2000) String notes
) {
}
