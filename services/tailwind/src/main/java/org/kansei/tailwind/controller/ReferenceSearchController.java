package org.kansei.tailwind.controller;

import org.kansei.tailwind.dto.AircraftTypeResponse;
import org.kansei.tailwind.dto.AirlineResponse;
import org.kansei.tailwind.dto.AirportResponse;
import org.kansei.tailwind.dto.CountryResponse;
import org.kansei.tailwind.service.ReferenceSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Typeahead and lookup over the reference data, backs manual flight entry. Requiring X-User-Id keeps
 * it behind the gateway's JWT check like every other tailwind route.
 */
@RestController
@RequestMapping("/tailwind")
public class ReferenceSearchController {

    private final ReferenceSearchService referenceSearchService;

    public ReferenceSearchController(ReferenceSearchService referenceSearchService) {
        this.referenceSearchService = referenceSearchService;
    }

    @GetMapping("/airports/search")
    public List<AirportResponse> searchAirports(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("q") String query,
            @RequestParam(required = false) Integer limit
    ) {
        return referenceSearchService.searchAirports(query, limit);
    }

    @GetMapping("/airlines/search")
    public List<AirlineResponse> searchAirlines(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("q") String query,
            @RequestParam(required = false) Integer limit
    ) {
        return referenceSearchService.searchAirlines(query, limit);
    }

    // Manual entry needs an airline, so the flight number offers one: TK1044 suggests Turkish Airlines
    @GetMapping("/airlines/for-flight-number")
    public List<AirlineResponse> airlinesForFlightNumber(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("flightNumber") String flightNumber
    ) {
        return referenceSearchService.airlinesForFlightNumber(flightNumber);
    }

    @GetMapping("/aircraft-types/search")
    public List<AircraftTypeResponse> searchAircraftTypes(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("q") String query,
            @RequestParam(required = false) Integer limit
    ) {
        return referenceSearchService.searchAircraftTypes(query, limit);
    }

    @GetMapping("/countries")
    public List<CountryResponse> listCountries(@RequestHeader("X-User-Id") UUID userId) {
        return referenceSearchService.listCountries();
    }
}
