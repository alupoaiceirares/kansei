package org.kansei.tailwind.service;

import org.kansei.tailwind.aircraft.AircraftResolver;
import org.kansei.tailwind.dto.AircraftTypeResponse;
import org.kansei.tailwind.dto.MapAircraftStringResponse;
import org.kansei.tailwind.dto.UnmappedAircraftString;
import org.kansei.tailwind.model.AircraftModelAlias;
import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.repository.AircraftModelAliasRepository;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.FlightRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin mapping of provider model strings the resolver could not place. A mapping becomes an alias, so later
 * flights resolve on their own, and the stored flights with a matching string are fixed at once.
 */
@Service
public class AircraftMappingService {

    private final AdminAuthService adminAuthService;
    private final FlightRepository flightRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final AircraftModelAliasRepository aliasRepository;
    private final AircraftResolver aircraftResolver;
    private final AuditPublisher auditPublisher;

    public AircraftMappingService(AdminAuthService adminAuthService, FlightRepository flightRepository, AircraftTypeRepository aircraftTypeRepository,
                                  AircraftModelAliasRepository aliasRepository, AircraftResolver aircraftResolver, AuditPublisher auditPublisher) {
        this.adminAuthService = adminAuthService;
        this.flightRepository = flightRepository;
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.aliasRepository = aliasRepository;
        this.aircraftResolver = aircraftResolver;
        this.auditPublisher = auditPublisher;
    }

    @Transactional(readOnly = true)
    public List<UnmappedAircraftString> listUnmapped(UUID adminId, String role) {
        adminAuthService.requireAdmin(adminId, role);
        return flightRepository.findUnmappedModelStrings();
    }

    @Transactional
    public MapAircraftStringResponse map(UUID adminId, String role, String modelString, Long aircraftTypeId) {
        adminAuthService.requireAdmin(adminId, role);
        AircraftType type = aircraftTypeRepository.findById(aircraftTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aircraft type not found"));
        String key = aircraftResolver.aliasKey(modelString);
        if (key.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing usable left in that model string to map");
        }
        aliasRepository.save(new AircraftModelAlias(key, type.getId()));

        // Every unresolved raw string with the same key gets the type, not only the exact spelling that was mapped
        List<String> sameKey = flightRepository.findUnmappedModelStrings().stream()
                .map(UnmappedAircraftString::modelString)
                .filter(raw -> key.equals(aircraftResolver.aliasKey(raw)))
                .toList();
        int updated = sameKey.isEmpty() ? 0 : flightRepository.assignAircraftType(sameKey, type, type.getFamily());

        auditPublisher.publishWithResolvedUsername("MAP_AIRCRAFT_STRING", adminId, "AIRCRAFT_TYPE", String.valueOf(type.getId()),
                Map.of("modelString", modelString, "aliasKey", key, "flightsUpdated", updated));
        return new MapAircraftStringResponse(key, AircraftTypeResponse.of(type), updated);
    }
}
