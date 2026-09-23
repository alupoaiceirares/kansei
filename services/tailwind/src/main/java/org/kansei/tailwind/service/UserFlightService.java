package org.kansei.tailwind.service;

import org.kansei.tailwind.dto.AddFlightRequest;
import org.kansei.tailwind.dto.ManualFlightRequest;
import org.kansei.tailwind.dto.UpdateUserFlightRequest;
import org.kansei.tailwind.dto.UserFlightResponse;
import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.model.Airline;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.Flight;
import org.kansei.tailwind.model.FlightSource;
import org.kansei.tailwind.model.Journey;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.model.TripReason;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.model.Visibility;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.FlightRepository;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Adds, edits and removes the flights in a user's log. Everything is scoped to the caller.
 */
@Service
@Transactional
public class UserFlightService {

    private final UserFlightRepository userFlightRepository;
    private final FlightRepository flightRepository;
    private final AirlineRepository airlineRepository;
    private final AirportRepository airportRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final JourneyService journeyService;
    private final JourneyViews journeyViews;
    private final OptInGuard optInGuard;
    private final Clock clock;
    private final int maxFutureDays;

    public UserFlightService(UserFlightRepository userFlightRepository, FlightRepository flightRepository, AirlineRepository airlineRepository,
                             AirportRepository airportRepository, AircraftTypeRepository aircraftTypeRepository, JourneyService journeyService,
                             JourneyViews journeyViews, OptInGuard optInGuard, Clock clock,
                             @Value("${tailwind.lookup.max-future-days}") int maxFutureDays) {
        this.userFlightRepository = userFlightRepository;
        this.flightRepository = flightRepository;
        this.airlineRepository = airlineRepository;
        this.airportRepository = airportRepository;
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.journeyService = journeyService;
        this.journeyViews = journeyViews;
        this.optInGuard = optInGuard;
        this.clock = clock;
        this.maxFutureDays = maxFutureDays;
    }

    // Confirms a looked-up flight into the log
    public UserFlightResponse add(UUID userId, AddFlightRequest request) {
        TailwindUser user = optInGuard.require(userId);
        Flight flight = flightRepository.findDetailedById(request.flightId())
                .filter(f -> f.getSource() == FlightSource.API)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        if (flight.isCargo() && !Boolean.TRUE.equals(request.confirmCargo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This is a cargo flight, confirm to add it");
        }
        if (userFlightRepository.existsByUserIdAndFlightId(userId, flight.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This flight is already in your log");
        }
        return save(userId, user, flight, request.visibility(), request.journeyId(), request.seat(), request.seatPosition(), request.cabinClass(),
                request.reason(), request.notes());
    }

    // A flight the provider does not have, stored as a private flight row that only this entry references
    public UserFlightResponse addManual(UUID userId, ManualFlightRequest request) {
        TailwindUser user = optInGuard.require(userId);
        if (request.date().isAfter(LocalDate.now(clock).plusDays(maxFutureDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That date is too far ahead");
        }
        Airline airline = airlineRepository.findById(request.airlineId()).orElseThrow(() -> badRequest("Unknown airline"));
        Airport departure = airportRepository.findById(request.departureAirportId()).orElseThrow(() -> badRequest("Unknown departure airport"));
        Airport arrival = airportRepository.findById(request.arrivalAirportId()).orElseThrow(() -> badRequest("Unknown arrival airport"));
        if (departure.getId().equals(arrival.getId())) {
            throw badRequest("Departure and arrival airport must differ");
        }
        AircraftType type = request.aircraftTypeId() == null ? null
                : aircraftTypeRepository.findById(request.aircraftTypeId()).orElseThrow(() -> badRequest("Unknown aircraft type"));

        Instant departureUtc = toUtc(request.date(), request.departureTime(), departure);
        Instant arrivalUtc = toUtc(request.date(), request.arrivalTime(), arrival);
        // A flight that lands after midnight local gives an arrival before its departure, so it rolls a day
        if (departureUtc != null && arrivalUtc != null && !arrivalUtc.isAfter(departureUtc)) {
            arrivalUtc = arrivalUtc.plus(1, ChronoUnit.DAYS);
        }

        Flight flight = flightRepository.saveAndFlush(Flight.builder()
                .source(FlightSource.MANUAL)
                .flightNumber(request.flightNumber())
                .flightDate(request.date())
                .airline(airline)
                .departureAirport(departure)
                .arrivalAirport(arrival)
                .cargo(Boolean.TRUE.equals(request.cargo()))
                .aircraftType(type)
                .aircraftFamily(type == null ? null : type.getFamily())
                .departureScheduledUtc(departureUtc)
                .arrivalScheduledUtc(arrivalUtc)
                .distanceKm(GeoDistance.haversineKm(departure.getLatitude(), departure.getLongitude(), arrival.getLatitude(), arrival.getLongitude()))
                .createdAt(clock.instant())
                .build());
        return save(userId, user, flight, request.visibility(), request.journeyId(), request.seat(), request.seatPosition(), request.cabinClass(),
                request.reason(), request.notes());
    }

    public UserFlightResponse update(UUID userId, Long userFlightId, UpdateUserFlightRequest request) {
        UserFlight userFlight = userFlightRepository.findDetailedByIdAndUserId(userFlightId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found in your log"));
        Long previousJourneyId = userFlight.getJourneyId();

        if (request.visibility() != null) {
            userFlight.setVisibility(request.visibility());
        }
        if (request.seat() != null) {
            userFlight.setSeat(blankToNull(request.seat()));
        }
        if (request.seatPosition() != null) {
            userFlight.setSeatPosition(request.seatPosition());
        }
        if (request.cabinClass() != null) {
            userFlight.setCabinClass(request.cabinClass());
        }
        if (request.reason() != null) {
            userFlight.setReason(request.reason());
        }
        if (request.stopType() != null) {
            userFlight.setStopType(request.stopType());
        }
        if (request.notes() != null) {
            userFlight.setNotes(blankToNull(request.notes()));
        }
        if (request.journeyId() != null && !request.journeyId().equals(previousJourneyId)) {
            userFlight.setJourneyId(journeyService.requireOwned(userId, request.journeyId()).getId());
        }
        userFlightRepository.flush();
        if (!userFlight.getJourneyId().equals(previousJourneyId)) {
            journeyService.deleteIfEmptyAndPlain(previousJourneyId);
        }
        return respond(userFlight);
    }

    @Transactional(readOnly = true)
    public UserFlightResponse get(UUID userId, Long userFlightId) {
        UserFlight userFlight = userFlightRepository.findDetailedByIdAndUserId(userFlightId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found in your log"));
        return respond(userFlight);
    }

    public void delete(UUID userId, Long userFlightId) {
        UserFlight userFlight = userFlightRepository.findDetailedByIdAndUserId(userFlightId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found in your log"));
        Long journeyId = userFlight.getJourneyId();
        Flight flight = userFlight.getFlight();
        userFlightRepository.delete(userFlight);
        userFlightRepository.flush();
        if (flight.getSource() == FlightSource.MANUAL) {
            flightRepository.delete(flight);
        }
        journeyService.deleteIfEmptyAndPlain(journeyId);
    }

    private UserFlightResponse save(UUID userId, TailwindUser user, Flight flight, Visibility visibility, Long journeyId, String seat,
                                    SeatPosition seatPosition, CabinClass cabinClass, TripReason reason, String notes) {
        Journey journey = journeyId == null ? journeyService.createAutomatic(userId) : journeyService.requireOwned(userId, journeyId);
        UserFlight saved = userFlightRepository.saveAndFlush(UserFlight.builder()
                .userId(userId)
                .journeyId(journey.getId())
                .flight(flight)
                .visibility(visibility != null ? visibility : user.getDefaultVisibility())
                .seat(blankToNull(seat))
                .seatPosition(seatPosition)
                .cabinClass(cabinClass)
                .reason(reason)
                .notes(blankToNull(notes))
                .createdAt(clock.instant())
                .build());
        return respond(saved);
    }

    // The stop type suggestion depends on the neighbouring flights, so the response is cut from the whole journey
    private UserFlightResponse respond(UserFlight userFlight) {
        List<UserFlight> journeyFlights = userFlightRepository.findDetailedByJourneyId(userFlight.getJourneyId());
        List<UserFlightResponse> responses = journeyViews.userFlights(journeyFlights);
        for (int i = 0; i < journeyFlights.size(); i++) {
            if (journeyFlights.get(i).getId().equals(userFlight.getId())) {
                return responses.get(i);
            }
        }
        throw new IllegalStateException("saved flight missing from its journey");
    }

    /**
     * A local clock time at its own airport becomes an instant. An airport with no time zone on file falls
     * back to UTC, which keeps the duration right whenever both ends share that fallback.
     */
    private static Instant toUtc(LocalDate date, LocalTime time, Airport airport) {
        if (time == null) {
            return null;
        }
        ZoneId zone = ZoneOffset.UTC;
        if (airport.getTimeZone() != null && !airport.getTimeZone().isBlank()) {
            try {
                zone = ZoneId.of(airport.getTimeZone());
            } catch (RuntimeException ex) {
                zone = ZoneOffset.UTC;
            }
        }
        return date.atTime(time).atZone(zone).toInstant();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
