package org.kansei.tailwind.service;

import org.kansei.tailwind.dto.JourneyResponse;
import org.kansei.tailwind.dto.UserFlightResponse;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.model.Journey;
import org.kansei.tailwind.model.StopType;
import org.kansei.tailwind.model.UserFlight;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the journey views: the automatic title and the suggested stop type between consecutive flights.
 */
@Component
public class JourneyViews {

    private static final Duration LAYOVER_LIMIT = Duration.ofHours(12);

    private final Clock clock;

    public JourneyViews(Clock clock) {
        this.clock = clock;
    }

    // flights must already be in chronological order
    public JourneyResponse journey(Journey journey, List<UserFlight> flights) {
        boolean custom = journey.getTitle() != null && !journey.getTitle().isBlank();
        return new JourneyResponse(journey.getId(), custom ? journey.getTitle() : autoTitle(flights), custom, journey.getNotes(),
                journey.getCreatedAt(), userFlights(flights));
    }

    public List<UserFlightResponse> userFlights(List<UserFlight> flights) {
        Instant now = clock.instant();
        List<UserFlightResponse> result = new ArrayList<>();
        for (int i = 0; i < flights.size(); i++) {
            StopType suggested = i + 1 < flights.size() ? suggestStopType(flights.get(i), flights.get(i + 1)) : null;
            result.add(UserFlightResponse.of(flights.get(i), suggested, now));
        }
        return result;
    }

    // A connection at the same airport within 12 hours is a layover, anything else counts as a stay
    public static StopType suggestStopType(UserFlight current, UserFlight next) {
        Airport arrival = current.getFlight().getArrivalAirport();
        Airport departure = next.getFlight().getDepartureAirport();
        if (!arrival.getId().equals(departure.getId())) {
            return StopType.STAY;
        }
        Instant arrives = current.getFlight().bestArrival();
        Instant departs = next.getFlight().bestDeparture();
        if (arrives == null || departs == null) {
            // No times to measure, only a connection on the same calendar day counts
            return ChronoUnit.DAYS.between(current.getFlight().getFlightDate(), next.getFlight().getFlightDate()) == 0 ? StopType.LAYOVER : StopType.STAY;
        }
        Duration gap = Duration.between(arrives, departs);
        return !gap.isNegative() && gap.compareTo(LAYOVER_LIMIT) <= 0 ? StopType.LAYOVER : StopType.STAY;
    }

    static String autoTitle(List<UserFlight> flights) {
        if (flights.isEmpty()) {
            return "Empty journey";
        }
        Airport origin = flights.get(0).getFlight().getDepartureAirport();
        Airport end = flights.get(flights.size() - 1).getFlight().getArrivalAirport();
        // A trip that ends where it started would read "OTP - OTP", the farthest airport reached says more
        Airport destination = origin.getId().equals(end.getId()) ? farthestFrom(origin, flights) : end;
        return code(origin) + " - " + code(destination);
    }

    private static Airport farthestFrom(Airport origin, List<UserFlight> flights) {
        Airport farthest = origin;
        double best = 0;
        for (UserFlight flight : flights) {
            Airport arrival = flight.getFlight().getArrivalAirport();
            double distance = GeoDistance.haversineKm(origin.getLatitude(), origin.getLongitude(), arrival.getLatitude(), arrival.getLongitude());
            if (distance > best) {
                best = distance;
                farthest = arrival;
            }
        }
        return farthest;
    }

    private static String code(Airport airport) {
        return airport.getIata() != null ? airport.getIata() : airport.getIcao();
    }
}
