package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.aircraft.AircraftResolver;
import org.kansei.tailwind.dto.UserFlightResponse;
import org.kansei.tailwind.flightdata.ExternalFlight;
import org.kansei.tailwind.flightdata.FlightDataClient;
import org.kansei.tailwind.flightdata.FlightDataUnavailableException;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.model.Flight;
import org.kansei.tailwind.model.FlightSource;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.repository.FlightRepository;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Swaps the schedule data of a flight looked up before it landed for what really happened, daily or from the
 * refresh button. Both spend lookup quota, the button also counts toward the per-user limit and a per-flight cooldown.
 */
@Slf4j
@Service
public class FlightRefreshService {

    private final FlightRepository flightRepository;
    private final UserFlightRepository userFlightRepository;
    private final UserFlightService userFlightService;
    private final FlightDataClient flightDataClient;
    private final AircraftResolver aircraftResolver;
    private final LookupQuotaGuard quotaGuard;
    private final LookupRateLimiter rateLimiter;
    private final RefreshCooldown refreshCooldown;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int windowDays;
    private final Duration grace;
    private final int maxPastDays;

    public FlightRefreshService(FlightRepository flightRepository, UserFlightRepository userFlightRepository, UserFlightService userFlightService,
                                FlightDataClient flightDataClient, AircraftResolver aircraftResolver, LookupQuotaGuard quotaGuard,
                                LookupRateLimiter rateLimiter, RefreshCooldown refreshCooldown, PlatformTransactionManager transactionManager,
                                Clock clock,
                                @Value("${tailwind.flight-refresh.window-days}") int windowDays,
                                @Value("${tailwind.flight-refresh.grace-hours}") int graceHours,
                                @Value("${tailwind.lookup.max-past-days}") int maxPastDays) {
        this.flightRepository = flightRepository;
        this.userFlightRepository = userFlightRepository;
        this.userFlightService = userFlightService;
        this.flightDataClient = flightDataClient;
        this.aircraftResolver = aircraftResolver;
        this.quotaGuard = quotaGuard;
        this.rateLimiter = rateLimiter;
        this.refreshCooldown = refreshCooldown;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.windowDays = windowDays;
        this.grace = Duration.ofHours(graceHours);
        this.maxPastDays = maxPastDays;
    }

    // The refresh button, works before departure too (gate times, aircraft swaps) as long as the flight still awaits a refresh
    public UserFlightResponse refresh(UUID userId, Long userFlightId) {
        UserFlight userFlight = userFlightRepository.findDetailedByIdAndUserId(userFlightId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found in your log"));
        Flight flight = userFlight.getFlight();
        if (flight.getSource() != FlightSource.API) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only flights found through the flight search can be refreshed");
        }
        if (!flight.isAwaitingRefresh()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This flight already has its final data");
        }
        if (flight.getFlightDate().isBefore(LocalDate.now(clock).minusDays(maxPastDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The flight data provider no longer has flights from that date");
        }

        refreshCooldown.start(flight.getId());
        try {
            rateLimiter.checkAndCount(userId);
            quotaGuard.reserveLookup();
        } catch (ResponseStatusException ex) {
            refreshCooldown.cancel(flight.getId());
            throw ex;
        }

        List<ExternalFlight> external;
        try {
            external = flightDataClient.fetchByNumber(flight.getFlightNumber(), flight.getFlightDate());
        } catch (FlightDataUnavailableException ex) {
            log.warn("flight data provider failed refreshing {} on {}: {}", flight.getFlightNumber(), flight.getFlightDate(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The flight data provider is not answering, try again later");
        }
        ExternalFlight leg = matchingLeg(flight, external)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The flight data provider has no update for this flight right now"));
        apply(flight.getId(), leg);
        return userFlightService.get(userId, userFlightId);
    }

    /**
     * Refreshes every flight that landed at least the grace period ago within the window, one provider call per
     * flight number and date. Stops as soon as the quota guard refuses. Returns how many flights got fresh data.
     */
    public int refreshLanded() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        Instant landedBy = now.minus(grace);

        Map<String, List<Flight>> byNumberAndDate = new LinkedHashMap<>();
        for (Flight flight : flightRepository.findAwaitingRefresh(today.minusDays(windowDays), today)) {
            if (!flight.isUpcoming(landedBy)) {
                byNumberAndDate.computeIfAbsent(flight.getFlightNumber() + "|" + flight.getFlightDate(), k -> new ArrayList<>()).add(flight);
            }
        }

        int refreshed = 0;
        for (List<Flight> group : byNumberAndDate.values()) {
            Flight first = group.get(0);
            try {
                quotaGuard.reserveLookup();
            } catch (ResponseStatusException ex) {
                log.warn("flight refresh stopped, the lookup budget refused: {}", ex.getReason());
                break;
            }
            try {
                List<ExternalFlight> external = flightDataClient.fetchByNumber(first.getFlightNumber(), first.getFlightDate());
                for (Flight flight : group) {
                    Optional<ExternalFlight> leg = matchingLeg(flight, external);
                    if (leg.isPresent()) {
                        apply(flight.getId(), leg.get());
                        refreshed++;
                    }
                }
            } catch (RuntimeException ex) {
                // Left awaiting, the next run inside the window tries again
                log.warn("could not refresh {} on {}: {}", first.getFlightNumber(), first.getFlightDate(), ex.getMessage());
            }
        }
        log.info("flight refresh done, {} of {} due flights updated", refreshed, byNumberAndDate.values().stream().mapToInt(List::size).sum());
        return refreshed;
    }

    private void apply(Long flightId, ExternalFlight leg) {
        transactionTemplate.executeWithoutResult(status -> {
            Flight flight = flightRepository.findById(flightId).orElse(null);
            if (flight == null) {
                return;
            }
            if (leg.status() != null) {
                flight.setStatus(leg.status());
            }
            flight.setDepartureScheduledUtc(firstNonNull(leg.departureScheduled(), flight.getDepartureScheduledUtc()));
            flight.setDepartureRevisedUtc(firstNonNull(leg.departureRevised(), flight.getDepartureRevisedUtc()));
            flight.setDepartureActualUtc(firstNonNull(leg.departureActual(), flight.getDepartureActualUtc()));
            flight.setArrivalScheduledUtc(firstNonNull(leg.arrivalScheduled(), flight.getArrivalScheduledUtc()));
            flight.setArrivalRevisedUtc(firstNonNull(leg.arrivalRevised(), flight.getArrivalRevisedUtc()));
            flight.setArrivalActualUtc(firstNonNull(leg.arrivalActual(), flight.getArrivalActualUtc()));

            ExternalFlight.Aircraft aircraft = leg.aircraft();
            if (aircraft != null && (aircraft.model() != null || aircraft.registration() != null)) {
                AircraftResolver.Resolution resolution = aircraftResolver.resolve(aircraft.model(), aircraft.registration());
                // A vaguer answer never wipes an aircraft we already resolved
                if (resolution.family() != null || flight.getAircraftFamily() == null) {
                    flight.setAircraftType(resolution.type());
                    flight.setAircraftFamily(resolution.family());
                }
                flight.setAircraftModelRaw(firstNonNull(aircraft.model(), flight.getAircraftModelRaw()));
                flight.setRegistration(firstNonNull(aircraft.registration(), flight.getRegistration()));
                flight.setModeS(firstNonNull(aircraft.modeS(), flight.getModeS()));
            }
            flight.setApiPayload(leg.rawJson());
            flight.setApiLastUpdatedUtc(leg.lastUpdated());
            flight.setAwaitingRefresh(flight.isUpcoming(clock.instant()));
            flightRepository.save(flight);
        });
    }

    // Same leg as the stored one: same local departure date and same departure airport
    static Optional<ExternalFlight> matchingLeg(Flight flight, List<ExternalFlight> external) {
        Airport departure = flight.getDepartureAirport();
        return external.stream()
                .filter(e -> e.flightDate() == null || e.flightDate().equals(flight.getFlightDate()))
                .filter(e -> e.departure() != null
                        && ((departure.getIcao() != null && departure.getIcao().equals(e.departure().icao()))
                        || (departure.getIata() != null && departure.getIata().equals(e.departure().iata()))))
                .findFirst();
    }

    private static <T> T firstNonNull(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }
}
