package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.aircraft.AircraftResolver;
import org.kansei.tailwind.dto.FlightLookupResponse;
import org.kansei.tailwind.dto.FlightResponse;
import org.kansei.tailwind.flightdata.ExternalFlight;
import org.kansei.tailwind.flightdata.FlightDataClient;
import org.kansei.tailwind.flightdata.FlightDataUnavailableException;
import org.kansei.tailwind.model.Airline;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.model.Flight;
import org.kansei.tailwind.model.FlightSource;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.CountryRepository;
import org.kansei.tailwind.repository.FlightRepository;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Flight number lookup. Stored flights are served first at no cost, only a flight nobody has looked up yet
 * goes to the provider, and whatever comes back is stored so nobody pays for it twice.
 */
@Slf4j
@Service
public class FlightLookupService {

    private static final Pattern FLIGHT_NUMBER = Pattern.compile("^[A-Z0-9]{2,3}\\d{1,4}[A-Z]?$");
    private static final String NEW_AIRPORT_TYPE = "unknown";

    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final AirlineRepository airlineRepository;
    private final CountryRepository countryRepository;
    private final UserFlightRepository userFlightRepository;
    private final AircraftResolver aircraftResolver;
    private final FlightDataClient flightDataClient;
    private final LookupQuotaGuard quotaGuard;
    private final LookupRateLimiter rateLimiter;
    private final LookupMissCache missCache;
    private final OptInGuard optInGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int maxPastDays;
    private final int maxFutureDays;

    public FlightLookupService(FlightRepository flightRepository, AirportRepository airportRepository, AirlineRepository airlineRepository,
                               CountryRepository countryRepository, UserFlightRepository userFlightRepository, AircraftResolver aircraftResolver,
                               FlightDataClient flightDataClient, LookupQuotaGuard quotaGuard, LookupRateLimiter rateLimiter,
                               LookupMissCache missCache, OptInGuard optInGuard, PlatformTransactionManager transactionManager, Clock clock,
                               @Value("${tailwind.lookup.max-past-days}") int maxPastDays,
                               @Value("${tailwind.lookup.max-future-days}") int maxFutureDays) {
        this.flightRepository = flightRepository;
        this.airportRepository = airportRepository;
        this.airlineRepository = airlineRepository;
        this.countryRepository = countryRepository;
        this.userFlightRepository = userFlightRepository;
        this.aircraftResolver = aircraftResolver;
        this.flightDataClient = flightDataClient;
        this.quotaGuard = quotaGuard;
        this.rateLimiter = rateLimiter;
        this.missCache = missCache;
        this.optInGuard = optInGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.maxPastDays = maxPastDays;
        this.maxFutureDays = maxFutureDays;
    }

    public FlightLookupResponse lookup(UUID userId, String rawFlightNumber, LocalDate date) {
        optInGuard.require(userId);
        String number = normalizeNumber(rawFlightNumber);
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today.minusDays(maxPastDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The flight data provider only has the last " + maxPastDays + " days, add this flight manually");
        }
        if (date.isAfter(today.plusDays(maxFutureDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That date is too far ahead for flight schedules");
        }

        List<Flight> flights = flightRepository.findDetailed(number, date, FlightSource.API);
        if (flights.isEmpty()) {
            flights = fetchAndStore(userId, number, date);
        }

        Instant now = clock.instant();
        return new FlightLookupResponse(flights.stream()
                .map(f -> new FlightLookupResponse.Entry(FlightResponse.of(f, now), userFlightRepository.existsByUserIdAndFlightId(userId, f.getId())))
                .toList());
    }

    private List<Flight> fetchAndStore(UUID userId, String number, LocalDate date) {
        if (missCache.isKnownMiss(number, date)) {
            throw notFound();
        }
        rateLimiter.checkAndCount(userId);
        quotaGuard.reserveLookup();

        List<ExternalFlight> external;
        try {
            external = flightDataClient.fetchByNumber(number, date);
        } catch (FlightDataUnavailableException ex) {
            log.warn("flight data provider failed for {} on {}: {}", number, date, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The flight data provider is not answering, try again later or add the flight manually");
        }

        // The provider also matches flights that arrive on the date, only keep the ones departing on it
        List<ExternalFlight> matching = external.stream().filter(f -> f.flightDate() == null || f.flightDate().equals(date)).toList();
        if (matching.isEmpty()) {
            missCache.remember(number, date);
            throw notFound();
        }

        try {
            return matching.stream().map(f -> storeWithRetry(f, number, date)).toList();
        } catch (FlightDataUnavailableException ex) {
            log.warn("could not store the provider flight {} on {}: {}", number, date, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The flight data provider sent incomplete data, add the flight manually");
        }
    }

    // A concurrent lookup of the same flight can win the unique index, or insert the same new airport first, so try once more
    private Flight storeWithRetry(ExternalFlight external, String number, LocalDate date) {
        try {
            return transactionTemplate.execute(status -> store(external, number, date));
        } catch (DataIntegrityViolationException ex) {
            return transactionTemplate.execute(status -> store(external, number, date));
        }
    }

    private Flight store(ExternalFlight external, String number, LocalDate date) {
        Airport departure = resolveAirport(external.departure());
        Airport arrival = resolveAirport(external.arrival());
        Airline airline = resolveAirline(external.airline());
        String flightNumber = external.flightNumber() != null ? external.flightNumber() : number;
        LocalDate flightDate = external.flightDate() != null ? external.flightDate() : date;

        List<Flight> existing = flightRepository.findDetailed(flightNumber, flightDate, FlightSource.API).stream()
                .filter(f -> f.getDepartureAirport().getId().equals(departure.getId())).toList();
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        ExternalFlight.Aircraft aircraft = external.aircraft();
        AircraftResolver.Resolution resolution = aircraftResolver.resolve(aircraft == null ? null : aircraft.model(),
                aircraft == null ? null : aircraft.registration());

        Flight flight = Flight.builder()
                .source(FlightSource.API)
                .flightNumber(flightNumber)
                .flightDate(flightDate)
                .airline(airline)
                .departureAirport(departure)
                .arrivalAirport(arrival)
                .status(external.status())
                .cargo(external.cargo())
                .aircraftType(resolution.type())
                .aircraftFamily(resolution.family())
                .aircraftModelRaw(aircraft == null ? null : aircraft.model())
                .registration(aircraft == null ? null : aircraft.registration())
                .modeS(aircraft == null ? null : aircraft.modeS())
                .departureScheduledUtc(external.departureScheduled())
                .departureRevisedUtc(external.departureRevised())
                .departureActualUtc(external.departureActual())
                .arrivalScheduledUtc(external.arrivalScheduled())
                .arrivalRevisedUtc(external.arrivalRevised())
                .arrivalActualUtc(external.arrivalActual())
                .distanceKm(GeoDistance.haversineKm(departure.getLatitude(), departure.getLongitude(), arrival.getLatitude(), arrival.getLongitude()))
                .apiPayload(external.rawJson())
                .apiLastUpdatedUtc(external.lastUpdated())
                .createdAt(clock.instant())
                .build();
        // Anything not landed yet is schedule data, the refresh after landing replaces it
        flight.setAwaitingRefresh(flight.isUpcoming(clock.instant()));
        return flightRepository.saveAndFlush(flight);
    }

    // Uses our own airport when we have it (fills a missing time zone), otherwise stores what the provider sent
    private Airport resolveAirport(ExternalFlight.Airport provided) {
        if (provided == null || (provided.icao() == null && provided.iata() == null)) {
            throw new FlightDataUnavailableException("flight without an airport code");
        }
        Airport airport = (provided.icao() == null ? java.util.Optional.<Airport>empty() : airportRepository.findByIcao(provided.icao()))
                .or(() -> provided.iata() == null ? java.util.Optional.empty() : airportRepository.findByIata(provided.iata()))
                .orElse(null);
        if (airport != null) {
            if (airport.getTimeZone() == null && validZone(provided.timeZone())) {
                airport.setTimeZone(provided.timeZone());
                airportRepository.save(airport);
            }
            return airport;
        }
        if (provided.countryCode() == null || !countryRepository.existsById(provided.countryCode())
                || provided.latitude() == null || provided.longitude() == null || provided.name() == null) {
            throw new FlightDataUnavailableException("unknown airport " + (provided.icao() != null ? provided.icao() : provided.iata()) + " without usable details");
        }
        return airportRepository.saveAndFlush(Airport.builder()
                .icao(provided.icao())
                .iata(provided.iata())
                .name(provided.name())
                .city(provided.city())
                .countryCode(provided.countryCode())
                .latitude(provided.latitude())
                .longitude(provided.longitude())
                .timeZone(validZone(provided.timeZone()) ? provided.timeZone() : null)
                .airportType(NEW_AIRPORT_TYPE)
                .build());
    }

    private Airline resolveAirline(ExternalFlight.Airline provided) {
        if (provided == null || (provided.icao() == null && provided.iata() == null)) {
            throw new FlightDataUnavailableException("flight without an airline");
        }
        if (provided.icao() != null) {
            Airline byIcao = airlineRepository.findByIcao(provided.icao()).orElse(null);
            if (byIcao != null) {
                return byIcao;
            }
        } else {
            List<Airline> byIata = airlineRepository.findByIataAndActiveTrue(provided.iata());
            if (byIata.size() == 1) {
                return byIata.get(0);
            }
            throw new FlightDataUnavailableException("airline " + provided.iata() + " has no ICAO code and is not unique in our data");
        }
        return airlineRepository.saveAndFlush(Airline.builder()
                .icao(provided.icao())
                .iata(provided.iata())
                .name(provided.name() != null ? provided.name() : provided.icao())
                .build());
    }

    private static boolean validZone(String zone) {
        return zone != null && ZoneId.getAvailableZoneIds().contains(zone);
    }

    static String normalizeNumber(String raw) {
        String number = raw == null ? "" : raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!FLIGHT_NUMBER.matcher(number).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "flightNumber must look like LH400");
        }
        return number;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No flight found for that number and date, you can add it manually");
    }
}
