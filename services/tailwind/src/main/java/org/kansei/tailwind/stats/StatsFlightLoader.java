package org.kansei.tailwind.stats;

import org.kansei.tailwind.model.StopType;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.model.Visibility;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.kansei.tailwind.service.JourneyViews;
import org.kansei.tailwind.service.ViewerAccess;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
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
 * Loads the flights a viewer may count for an owner, already flattened. Upcoming flights are left out until
 * they have happened, canceled ones for good.
 *
 * <p>Which flights a viewer may count is decided by ViewerAccess alone, so no aggregation can leak a hidden
 * flight into a total.
 */
@Component
public class StatsFlightLoader {

    private final UserFlightRepository userFlightRepository;
    private final ViewerAccess viewerAccess;
    private final Clock clock;

    public StatsFlightLoader(UserFlightRepository userFlightRepository, ViewerAccess viewerAccess, Clock clock) {
        this.userFlightRepository = userFlightRepository;
        this.viewerAccess = viewerAccess;
        this.clock = clock;
    }

    /**
     * @param viewerId who is asking, the owner sees everything of their own
     * @param ownerId  whose profile it is
     */
    @Transactional(readOnly = true)
    public List<StatsFlight> load(UUID viewerId, UUID ownerId, StatsModels.Period period) {
        List<UserFlight> flights = userFlightRepository.findDetailedByUserId(ownerId);
        Set<Visibility> allowed = viewerAccess.visibleTo(viewerId, ownerId);
        Instant now = clock.instant();

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
            if (uf.getFlight().isUpcoming(now) || uf.getFlight().isCanceled()) {
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
                f.getAircraftFamily(), uf.getCabinClass(), uf.getSeatPosition(), uf.getReason());
    }

    LocalDate todayUtc() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
