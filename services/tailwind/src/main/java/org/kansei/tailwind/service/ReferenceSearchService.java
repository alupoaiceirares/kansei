package org.kansei.tailwind.service;

import org.kansei.tailwind.dto.AircraftTypeResponse;
import org.kansei.tailwind.dto.AirlineResponse;
import org.kansei.tailwind.dto.AirportResponse;
import org.kansei.tailwind.dto.CountryResponse;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.CountryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReferenceSearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 25;
    // What follows the airline code in a flight number: one to four digits, sometimes a trailing letter
    private static final Pattern FLIGHT_SUFFIX = Pattern.compile("^[0-9]{1,4}[A-Z]?$");

    private final AirportRepository airportRepository;
    private final AirlineRepository airlineRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final CountryRepository countryRepository;

    public ReferenceSearchService(AirportRepository airportRepository, AirlineRepository airlineRepository,
                                  AircraftTypeRepository aircraftTypeRepository, CountryRepository countryRepository) {
        this.airportRepository = airportRepository;
        this.airlineRepository = airlineRepository;
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.countryRepository = countryRepository;
    }

    public List<AirportResponse> searchAirports(String query, Integer limit) {
        String q = normalize(query);
        String like = escapeLike(q.toLowerCase(Locale.ROOT));
        return airportRepository.search(q.toUpperCase(Locale.ROOT), like + "%", "%" + like + "%", page(limit)).stream()
                .map(AirportResponse::of).toList();
    }

    public List<AirlineResponse> searchAirlines(String query, Integer limit) {
        String q = normalize(query);
        return airlineRepository.search(q.toUpperCase(Locale.ROOT), "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%", page(limit)).stream()
                .map(AirlineResponse::of).toList();
    }

    public List<AircraftTypeResponse> searchAircraftTypes(String query, Integer limit) {
        String q = normalize(query);
        return aircraftTypeRepository.search(q.toUpperCase(Locale.ROOT), "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%", page(limit)).stream()
                .map(AircraftTypeResponse::of).toList();
    }

    /**
     * The airline that operates a flight number, worked out from its prefix: TK1044 is Turkish Airlines.
     * Manual entry needs an airline, and a user often remembers the number but not the carrier.
     */
    public List<AirlineResponse> airlinesForFlightNumber(String flightNumber) {
        String prefix = prefixOf(flightNumber);
        if (prefix == null) {
            return List.of();
        }
        // A two character prefix is an IATA code, three is ICAO, and both are checked either way
        return airlineRepository.findByCode(prefix).stream().map(AirlineResponse::of).toList();
    }

    public List<CountryResponse> listCountries() {
        return countryRepository.findAll(Sort.by("name")).stream().map(CountryResponse::of).toList();
    }

    /**
     * The airline code a flight number starts with: LH400 gives LH, DLH400 gives DLH, W63021 gives W6.
     * The two character code is tried first, so a digit never gets pulled into the code.
     */
    static String prefixOf(String flightNumber) {
        String value = flightNumber == null ? "" : flightNumber.trim().toUpperCase(Locale.ROOT);
        for (int length = 2; length <= 3 && length < value.length(); length++) {
            String prefix = value.substring(0, length);
            // A code holds at least one letter, so a bare 400 is a number and not an airline
            boolean hasLetter = prefix.chars().anyMatch(Character::isLetter);
            Matcher rest = FLIGHT_SUFFIX.matcher(value.substring(length));
            if (hasLetter && rest.matches()) {
                return prefix;
            }
        }
        return null;
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String normalize(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must be at least " + MIN_QUERY_LENGTH + " characters");
        }
        return q;
    }

    private static PageRequest page(Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        return PageRequest.of(0, size);
    }
}
