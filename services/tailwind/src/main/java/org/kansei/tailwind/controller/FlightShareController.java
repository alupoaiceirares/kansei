package org.kansei.tailwind.controller;

import org.kansei.tailwind.dto.FlightJoinRequestResponse;
import org.kansei.tailwind.dto.JoinFlightResult;
import org.kansei.tailwind.dto.SharedFlightFriend;
import org.kansei.tailwind.service.FlightShareService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Flights taken together: friends on the same flight, and "I was on this too" with the owner's approval when needed.
 */
@RestController
@RequestMapping("/tailwind/flights")
public class FlightShareController {

    private final FlightShareService flightShareService;

    public FlightShareController(FlightShareService flightShareService) {
        this.flightShareService = flightShareService;
    }

    @GetMapping("/{userFlightId}/shared")
    public List<SharedFlightFriend> shared(@RequestHeader("X-User-Id") UUID userId, @PathVariable Long userFlightId) {
        return flightShareService.sharedWith(userId, userFlightId);
    }

    // userFlightId is the other person's entry
    @PostMapping("/{userFlightId}/join")
    public JoinFlightResult join(@RequestHeader("X-User-Id") UUID userId, @PathVariable Long userFlightId) {
        return flightShareService.join(userId, userFlightId);
    }

    @GetMapping("/join-requests")
    public List<FlightJoinRequestResponse> requests(@RequestHeader("X-User-Id") UUID userId) {
        return flightShareService.requests(userId);
    }

    @PostMapping("/join-requests/{requestId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@RequestHeader("X-User-Id") UUID userId, @PathVariable Long requestId) {
        flightShareService.accept(userId, requestId);
    }

    @PostMapping("/join-requests/{requestId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@RequestHeader("X-User-Id") UUID userId, @PathVariable Long requestId) {
        flightShareService.decline(userId, requestId);
    }

    // The requester changing their mind
    @DeleteMapping("/join-requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@RequestHeader("X-User-Id") UUID userId, @PathVariable Long requestId) {
        flightShareService.withdraw(userId, requestId);
    }
}
