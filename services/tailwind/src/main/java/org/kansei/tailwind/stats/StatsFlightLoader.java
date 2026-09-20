package org.kansei.tailwind.stats;

import org.kansei.tailwind.model.StopType;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.model.Visibility;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.kansei.tailwind.service.JourneyViews;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Loads the flights a viewer may count for an owner, already flattened. Upcoming flights are left out, they
 * only count once they have happened.
 *
 * <p>The visibility filter is applied here and nowhere else, so no aggregation can leak a hidden flight into
 * a total. Phase 6 adds friends, which is the one place this needs to change.
 */
@Component
public class StatsFlightLoader {

    private final UserFlightRepository userFlightRepository;
    private final Clock clock;

    public StatsFlightLoader(UserFlightRepository userFlightRepository, Clock clock) {
        this.userFlightRepository = userFlightRepository;
        this.clock = clock;
    }

    /**
     * @param viewerId who is asking, the owner sees everything of their own
     * @param ownerId  whose profile it is
     */
    @Transactional(readOnly = true)
    public List<StatsFlight> load(UUID viewerId, UUID ownerId, StatsModels.Period period) {
        List<UserFlight> flights = userFlightRepository.findDetailedByUserId(ownerId);
        Set<Visibility> allowed = allowedVisibilities(viewerId, ownerId);
        LocalDate today = LocalDate.now(clock);

        // The stop type suggestion needs the neighbours inside the same journey, so it is worked out before filtering
        Map<Long, List<UserFlight>> byJourney = flights.stream().collect(Collectors.groupingBy(UserFlight::getJourneyId));
        Map<Long, StopType> suggestions = new java.util.HashMap<>();
        for (List<UserFlight> journeyFlights : byJourney.values()) {
            for (int i = 0; i + 1 < journeyFlights.size(); i++) {
                suggestions.put(journeyFlights.get(i).getId(), JourneyViews.suggestStopType(journeyFlights.get(i), journeyFlights.get(i + 1)));
            }
        }

        List<StatsFlight> result = new ArrayList<>();
        for (UserFlight uf : flights) {
            if (!allowed.contains(uf.getVisibility())) {
                continue;
            }
            if (hasNotHappenedYet(uf, today)) {
                continue;
            }
            if (outsidePeriod(uf.getFlight().getFlightDate(), period)) {
                continue;
            }
            result.add(flatten(uf, suggestions.get(uf.getId())));
        }
        result.sort(Comparator.comparing(StatsFlight::date).thenComparing(StatsFlight::userFlightId));
        return result;
    }

    private static Set<Visibility> allowedVisibilities(UUID viewerId, UUID ownerId) {
        if (viewerId.equals(ownerId)) {
            return Set.of(Visibility.PRIVATE, Visibility.FRIENDS, Visibility.PUBLIC);
        }
        // Friends come in the social phase, until then another viewer sees only public flights
        return Set.of(Visibility.PUBLIC);
    }

    // Uses the arrival time when there is one, otherwise the flight date, so a flight counts from the day after at the latest
    private static boolean hasNotHappenedYet(UserFlight uf, LocalDate today) {
        var arrival = uf.getFlight().bestArrival();
        if (arrival != null) {
            return arrival.isAfter(java.time.Instant.now());
        }
        return uf.getFlight().getFlightDate().isAfter(today);
    }

    private static boolean outsidePeriod(LocalDate date, StatsModels.Period period) {
        if (period == null) {
            return false;
        }
        return (period.from() != null && date.isBefore(period.from())) || (period.to() != null && date.isAfter(period.to()));
    }

    private static StatsFlight flatten(UserFlight uf, StopType suggested) {
        var f = uf.getFlight();
        var airline = f.getAirline();
        var departure = f.getDepartureAirport();
        var arrival = f.getArrivalAirport();
        var type = f.getAircraftType();
        return new StatsFlight(
                uf.getId(), uf.getJourneyId(), f.getId(), f.getFlightDate(), f.getFlightNumber(), f.getDistanceKm(), f.isCargo(),
                uf.getStopType(), suggested, f.bestDeparture(), f.bestArrival(),
                airline.getId(), airline.getIcao(), airline.getIata(), airline.getName(),
                departure.getId(), departure.getIcao(), departure.getIata(), departure.getName(), departure.getCity(),
                departure.getCountryCode(), departure.getLatitude(), departure.getLongitude(),
                arrival.getId(), arrival.getIcao(), arrival.getIata(), arrival.getName(), arrival.getCity(),
                arrival.getCountryCode(), arrival.getLatitude(), arrival.getLongitude(),
                type == null ? null : type.getId(), type == null ? null : type.getIcaoCode(), type == null ? null : type.getName(),
                type == null ? null : type.getManufacturer(), type == null || type.getBodyType() == null ? null : type.getBodyType().name(),
                f.getAircraftFamily());
    }

    LocalDate todayUtc() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
