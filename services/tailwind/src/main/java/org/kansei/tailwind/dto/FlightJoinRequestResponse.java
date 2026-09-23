package org.kansei.tailwind.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * INCOMING waits on the caller's approval, OUTGOING on someone else's. otherUser is the requester for INCOMING
 * and the owner for OUTGOING.
 */
public record FlightJoinRequestResponse(Long id, Direction direction, UUID otherUserId, String otherUsername, Long userFlightId,
                                        FlightResponse flight, Instant requestedAt) {

    public enum Direction {INCOMING, OUTGOING}
}
