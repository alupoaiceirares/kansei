package org.kansei.tailwind.dto;

/**
 * ADDED means the flight is in the caller's log now (userFlight is set), REQUESTED means the owner has to approve.
 */
public record JoinFlightResult(Outcome outcome, UserFlightResponse userFlight) {

    public enum Outcome {ADDED, REQUESTED}
}
