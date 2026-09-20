package org.kansei.tailwind.flightdata;

public class FlightDataUnavailableException extends RuntimeException {

    public FlightDataUnavailableException(String message) {
        super(message);
    }

    public FlightDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
