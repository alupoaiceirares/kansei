package org.kansei.tailwind.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.TripReason;
import org.kansei.tailwind.model.Visibility;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A flight the provider does not have (old, private, charter, budget gone). Airline and both airports come
 * from the reference data, the aircraft type is optional.
 */
public record ManualFlightRequest(
        @Pattern(regexp = "^[A-Z0-9]{2,3}\\d{1,4}[A-Z]?$", message = "flightNumber must look like LH400") String flightNumber,
        @NotNull LocalDate date,
        @NotNull Long airlineId,
        @NotNull Long departureAirportId,
        @NotNull Long arrivalAirportId,
        Long aircraftTypeId,
        // Local clock times at their own airport, both optional. Without them the flight still counts for
        // distance, it is only left out of time in air.
        LocalTime departureTime,
        LocalTime arrivalTime,
        Boolean cargo,
        Visibility visibility,
        Long journeyId,
        @Size(max = 8) String seat,
        SeatPosition seatPosition,
        CabinClass cabinClass,
        TripReason reason,
        @Size(max = 2000) String notes
) {
}
