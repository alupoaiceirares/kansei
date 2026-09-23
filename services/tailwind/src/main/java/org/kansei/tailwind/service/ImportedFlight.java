package org.kansei.tailwind.service;

import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.model.Airline;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.TripReason;
import org.kansei.tailwind.model.Visibility;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One CSV import row after every code was resolved against the reference data. aircraftType, aircraftFamily
 * and aircraftModelRaw are all null when the row named no aircraft, visibility is null when the row left it to the default.
 */
public record ImportedFlight(
        LocalDate date,
        String flightNumber,
        Airline airline,
        Airport departure,
        Airport arrival,
        LocalTime departureTime,
        LocalTime arrivalTime,
        AircraftType aircraftType,
        String aircraftFamily,
        String aircraftModelRaw,
        String seat,
        SeatPosition seatPosition,
        CabinClass cabinClass,
        TripReason reason,
        String notes,
        boolean cargo,
        String journey,
        Visibility visibility
) {
}
