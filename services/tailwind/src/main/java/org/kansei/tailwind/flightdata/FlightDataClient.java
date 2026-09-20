package org.kansei.tailwind.flightdata;

import java.time.LocalDate;
import java.util.List;

/**
 * Source of flight data by flight number and local departure date. Each call costs quota, callers go
 * through the stored flights first.
 */
public interface FlightDataClient {

    /**
     * One element per leg, empty when the provider has no such flight.
     *
     * @throws FlightDataUnavailableException when the provider cannot be reached or answers with an error
     */
    List<ExternalFlight> fetchByNumber(String flightNumber, LocalDate date);
}
