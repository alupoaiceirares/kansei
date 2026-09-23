package org.kansei.tailwind.controller;

import jakarta.validation.Valid;
import org.kansei.tailwind.dto.MapAircraftStringRequest;
import org.kansei.tailwind.dto.MapAircraftStringResponse;
import org.kansei.tailwind.dto.UnmappedAircraftString;
import org.kansei.tailwind.service.AircraftMappingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only list of unresolved aircraft model strings and the mapping onto an aircraft type.
 */
@RestController
@RequestMapping("/tailwind/admin/aircraft-strings")
public class AircraftMappingController {

    private final AircraftMappingService aircraftMappingService;

    public AircraftMappingController(AircraftMappingService aircraftMappingService) {
        this.aircraftMappingService = aircraftMappingService;
    }

    @GetMapping("/unmapped")
    public List<UnmappedAircraftString> unmapped(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return aircraftMappingService.listUnmapped(userId, role);
    }

    @PostMapping("/mappings")
    public MapAircraftStringResponse map(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody MapAircraftStringRequest request
    ) {
        return aircraftMappingService.map(userId, role, request.modelString(), request.aircraftTypeId());
    }
}
