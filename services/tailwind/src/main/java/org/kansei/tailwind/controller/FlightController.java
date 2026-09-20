package org.kansei.tailwind.controller;

import jakarta.validation.Valid;
import org.kansei.tailwind.dto.AddFlightRequest;
import org.kansei.tailwind.dto.FlightLookupResponse;
import org.kansei.tailwind.dto.ManualFlightRequest;
import org.kansei.tailwind.dto.UpdateUserFlightRequest;
import org.kansei.tailwind.dto.UserFlightResponse;
import org.kansei.tailwind.service.FlightLookupService;
import org.kansei.tailwind.service.JourneyService;
import org.kansei.tailwind.service.UserFlightService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Flight lookup and the user's own flight entries. X-User-Id is injected by control-tower from the verified JWT.
 */
@RestController
@RequestMapping("/tailwind/flights")
public class FlightController {

    private final FlightLookupService flightLookupService;
    private final UserFlightService userFlightService;
    private final JourneyService journeyService;

    public FlightController(FlightLookupService flightLookupService, UserFlightService userFlightService,
                            JourneyService journeyService) {
        this.flightLookupService = flightLookupService;
        this.userFlightService = userFlightService;
        this.journeyService = journeyService;
    }

    // Shows the details for the confirm screen, nothing is added to the user's log yet
    @GetMapping("/lookup")
    public FlightLookupResponse lookup(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam String flightNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return flightLookupService.lookup(userId, flightNumber, date);
    }

    // Confirms a looked-up flight into the log, a cargo flight needs confirmCargo=true
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserFlightResponse add(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody AddFlightRequest request) {
        return userFlightService.add(userId, request);
    }

    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public UserFlightResponse addManual(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody ManualFlightRequest request) {
        return userFlightService.addManual(userId, request);
    }

    // Every flight the user has logged, journeys hold the same flights grouped
    @GetMapping("/mine")
    public List<UserFlightResponse> mine(@RequestHeader("X-User-Id") UUID userId) {
        return journeyService.list(userId).stream().flatMap(j -> j.flights().stream()).toList();
    }

    @PatchMapping("/{userFlightId}")
    public UserFlightResponse update(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long userFlightId,
            @Valid @RequestBody UpdateUserFlightRequest request
    ) {
        return userFlightService.update(userId, userFlightId, request);
    }

    @DeleteMapping("/{userFlightId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-User-Id") UUID userId, @PathVariable Long userFlightId) {
        userFlightService.delete(userId, userFlightId);
    }
}
