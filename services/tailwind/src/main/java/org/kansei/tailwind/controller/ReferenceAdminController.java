package org.kansei.tailwind.controller;

import jakarta.validation.Valid;
import org.kansei.tailwind.dto.AircraftTypeRequest;
import org.kansei.tailwind.dto.AircraftTypeResponse;
import org.kansei.tailwind.dto.AirlineRequest;
import org.kansei.tailwind.dto.AirlineResponse;
import org.kansei.tailwind.dto.AirportRequest;
import org.kansei.tailwind.dto.AirportResponse;
import org.kansei.tailwind.dto.CountryRequest;
import org.kansei.tailwind.dto.CountryResponse;
import org.kansei.tailwind.service.ReferenceAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-only add and edit for the reference data, the gate and the audit trail live in ReferenceAdminService.
 */
@RestController
@RequestMapping("/tailwind/admin")
public class ReferenceAdminController {

    private final ReferenceAdminService referenceAdminService;

    public ReferenceAdminController(ReferenceAdminService referenceAdminService) {
        this.referenceAdminService = referenceAdminService;
    }

    @PostMapping("/airports")
    @ResponseStatus(HttpStatus.CREATED)
    public AirportResponse createAirport(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody AirportRequest request
    ) {
        return referenceAdminService.createAirport(userId, role, request);
    }

    @PutMapping("/airports/{id}")
    public AirportResponse updateAirport(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id,
            @Valid @RequestBody AirportRequest request
    ) {
        return referenceAdminService.updateAirport(userId, role, id, request);
    }

    @PostMapping("/airlines")
    @ResponseStatus(HttpStatus.CREATED)
    public AirlineResponse createAirline(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody AirlineRequest request
    ) {
        return referenceAdminService.createAirline(userId, role, request);
    }

    @PutMapping("/airlines/{id}")
    public AirlineResponse updateAirline(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id,
            @Valid @RequestBody AirlineRequest request
    ) {
        return referenceAdminService.updateAirline(userId, role, id, request);
    }

    @PostMapping("/aircraft-types")
    @ResponseStatus(HttpStatus.CREATED)
    public AircraftTypeResponse createAircraftType(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody AircraftTypeRequest request
    ) {
        return referenceAdminService.createAircraftType(userId, role, request);
    }

    @PutMapping("/aircraft-types/{id}")
    public AircraftTypeResponse updateAircraftType(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id,
            @Valid @RequestBody AircraftTypeRequest request
    ) {
        return referenceAdminService.updateAircraftType(userId, role, id, request);
    }

    @PostMapping("/countries")
    @ResponseStatus(HttpStatus.CREATED)
    public CountryResponse createCountry(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CountryRequest request
    ) {
        return referenceAdminService.createCountry(userId, role, request);
    }

    @PutMapping("/countries/{code}")
    public CountryResponse updateCountry(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String code,
            @Valid @RequestBody CountryRequest request
    ) {
        return referenceAdminService.updateCountry(userId, role, code, request);
    }
}
