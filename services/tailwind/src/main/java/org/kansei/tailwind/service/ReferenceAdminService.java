package org.kansei.tailwind.service;

import org.kansei.tailwind.dto.AircraftTypeRequest;
import org.kansei.tailwind.dto.AircraftTypeResponse;
import org.kansei.tailwind.dto.AirlineRequest;
import org.kansei.tailwind.dto.AirlineResponse;
import org.kansei.tailwind.dto.AirportRequest;
import org.kansei.tailwind.dto.AirportResponse;
import org.kansei.tailwind.dto.CountryRequest;
import org.kansei.tailwind.dto.CountryResponse;
import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.model.Airline;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.model.Country;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.CountryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Admin add and edit for the reference data. Every action is admin-gated and audited after it succeeds.
 */
@Service
public class ReferenceAdminService {

    private static final String DEFAULT_AIRPORT_TYPE = "small_airport";

    private final AdminAuthService adminAuthService;
    private final AuditPublisher auditPublisher;
    private final AirportRepository airportRepository;
    private final AirlineRepository airlineRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final CountryRepository countryRepository;

    public ReferenceAdminService(AdminAuthService adminAuthService, AuditPublisher auditPublisher,
                                 AirportRepository airportRepository, AirlineRepository airlineRepository,
                                 AircraftTypeRepository aircraftTypeRepository, CountryRepository countryRepository) {
        this.adminAuthService = adminAuthService;
        this.auditPublisher = auditPublisher;
        this.airportRepository = airportRepository;
        this.airlineRepository = airlineRepository;
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.countryRepository = countryRepository;
    }

    public AirportResponse createAirport(UUID adminId, String role, AirportRequest request) {
        adminAuthService.requireAdmin(adminId, role);
        Airport saved = save(() -> airportRepository.saveAndFlush(applyAirport(new Airport(), request)));
        audit("CREATE_AIRPORT", adminId, "AIRPORT", String.valueOf(saved.getId()), airportDetails(saved));
        return AirportResponse.of(saved);
    }

    public AirportResponse updateAirport(UUID adminId, String role, Long id, AirportRequest request) {
        adminAuthService.requireAdmin(adminId, role);
        Airport existing = airportRepository.findById(id).orElseThrow(() -> notFound("Airport"));
        Airport saved = save(() -> airportRepository.saveAndFlush(applyAirport(existing, request)));
        audit("UPDATE_AIRPORT", adminId, "AIRPORT", String.valueOf(saved.getId()), airportDetails(saved));
        return AirportResponse.of(saved);
    }

    public AirlineResponse createAirline(UUID adminId, String role, AirlineRequest request) {
        adminAuthService.requireAdmin(adminId, role);
        Airline saved = save(() -> airlineRepository.saveAndFlush(applyAirline(new Airline(), request)));
        audit("CREATE_AIRLINE", adminId, "AIRLINE", String.valueOf(saved.getId()), Map.of("icao", saved.getIcao()));
        return AirlineResponse.of(saved);
    }

    public AirlineResponse updateAirline(UUID adminId, String role, Long id, AirlineRequest request) {
        adminAuthService.requireAdmin(adminId, role);
        Airline existing = airlineRepository.findById(id).orElseThrow(() -> notFound("Airline"));
        Airline saved = save(() -> airlineRepository.saveAndFlush(applyAirline(existing, request)));
        audit("UPDATE_AIRLINE", adminId, "AIRLINE", String.valueOf(saved.getId()), Map.of("icao", saved.getIcao()));
        return AirlineResponse.of(saved);
    }

    public AircraftTypeResponse createAircraftType(UUID adminId, String role, AircraftTypeRequest request) {
        adminAuthService.requireAdmin(adminId, role);
        AircraftType saved = save(() -> aircraftTypeRepository.saveAndFlush(applyAircraftType(new AircraftType(), request)));
        audit("CREATE_AIRCRAFT_TYPE", adminId, "AIRCRAFT_TYPE", String.valueOf(saved.getId()), Map.of("icaoCode", saved.getIcaoCode()));
        return AircraftTypeResponse.of(saved);
    }

    public AircraftTypeResponse updateAircraftType(UUID adminId, String role, Long id, AircraftTypeRequest request) {
        adminAuthService.requireAdmin(adminId, role);
        AircraftType existing = aircraftTypeRepository.findById(id).orElseThrow(() -> notFound("Aircraft type"));
        AircraftType saved = save(() -> aircraftTypeRepository.saveAndFlush(applyAircraftType(existing, request)));
        audit("UPDATE_AIRCRAFT_TYPE", adminId, "AIRCRAFT_TYPE", String.valueOf(saved.getId()), Map.of("icaoCode", saved.getIcaoCode()));
        return AircraftTypeResponse.of(saved);
    }

    public CountryResponse createCountry(UUID adminId, String role, CountryRequest request) {
        adminAuthService.requireAdmin(adminId, role);
        if (countryRepository.existsById(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Country code already exists");
        }
        Country saved = countryRepository.save(new Country(request.code(), request.name().trim(), request.continent()));
        audit("CREATE_COUNTRY", adminId, "COUNTRY", saved.getCode(), Map.of("code", saved.getCode()));
        return CountryResponse.of(saved);
    }

    // The code is the key, only name and continent change
    public CountryResponse updateCountry(UUID adminId, String role, String code, CountryRequest request) {
        adminAuthService.requireAdmin(adminId, role);
        Country existing = countryRepository.findById(code).orElseThrow(() -> notFound("Country"));
        existing.setName(request.name().trim());
        existing.setContinent(request.continent());
        Country saved = countryRepository.save(existing);
        audit("UPDATE_COUNTRY", adminId, "COUNTRY", saved.getCode(), Map.of("code", saved.getCode()));
        return CountryResponse.of(saved);
    }

    private Airport applyAirport(Airport airport, AirportRequest r) {
        if (r.icao() == null && r.iata() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "icao or iata is required");
        }
        requireCountry(r.countryCode());
        if (r.timeZone() != null && !r.timeZone().isBlank() && !ZoneId.getAvailableZoneIds().contains(r.timeZone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown timeZone");
        }
        airport.setIcao(r.icao());
        airport.setIata(r.iata());
        airport.setName(r.name().trim());
        airport.setCity(blankToNull(r.city()));
        airport.setCountryCode(r.countryCode());
        airport.setLatitude(r.latitude());
        airport.setLongitude(r.longitude());
        airport.setTimeZone(blankToNull(r.timeZone()));
        airport.setAirportType(blankToNull(r.airportType()) == null ? DEFAULT_AIRPORT_TYPE : r.airportType());
        return airport;
    }

    private Airline applyAirline(Airline airline, AirlineRequest r) {
        if (r.countryCode() != null) {
            requireCountry(r.countryCode());
        }
        airline.setIcao(r.icao());
        airline.setIata(r.iata());
        airline.setName(r.name().trim());
        airline.setCountryCode(r.countryCode());
        airline.setActive(r.active() == null || r.active());
        return airline;
    }

    private AircraftType applyAircraftType(AircraftType type, AircraftTypeRequest r) {
        String manufacturer = r.manufacturer().trim();
        String model = r.model().trim();
        type.setIcaoCode(r.icaoCode());
        type.setManufacturer(manufacturer);
        type.setModel(model);
        type.setName(blankToNull(r.name()) == null ? manufacturer + " " + model : r.name().trim());
        type.setFamily(blankToNull(r.family()) == null ? model : r.family().trim());
        type.setBodyType(r.bodyType());
        type.setEngineType(r.engineType());
        type.setEngineCount(r.engineCount());
        type.setWakeCategory(r.wakeCategory());
        return type;
    }

    private void requireCountry(String countryCode) {
        if (!countryRepository.existsById(countryCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown countryCode");
        }
    }

    // A duplicate icao or iata trips the table's unique constraint, report it as a conflict instead of a 500
    private <T> T save(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A row with that code already exists");
        }
    }

    private Map<String, Object> airportDetails(Airport a) {
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("icao", a.getIcao());
        details.put("iata", a.getIata());
        return details;
    }

    private void audit(String action, UUID adminId, String targetType, String targetId, Map<String, Object> details) {
        auditPublisher.publishWithResolvedUsername(action, adminId, targetType, targetId, details);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
