package org.kansei.tailwind.stats;

import org.kansei.tailwind.model.StopType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out which countries a user was actually in, and which they only passed through.
 *
 * <p>Per journey: the first departure and the final arrival always count, and a stop in between counts only
 * when its stop type is STAY or LAYOVER_VISITED. A plain LAYOVER is passed through, not visited. Computed
 * from the visible flights every time, never stored, so a hidden flight cannot leak a country into a total.
 */
public final class VisitedCountriesCalculator {

    private VisitedCountriesCalculator() {
    }

    private record Visit(String countryCode, LocalDate date) {
    }

    public static StatsModels.VisitedCountries calculate(List<StatsFlight> flights, Map<String, CountryInfo> countries) {
        List<Visit> visited = new ArrayList<>();
        List<Visit> passedThrough = new ArrayList<>();

        Map<Long, List<StatsFlight>> byJourney = new LinkedHashMap<>();
        for (StatsFlight flight : flights) {
            byJourney.computeIfAbsent(flight.journeyId(), id -> new ArrayList<>()).add(flight);
        }

        for (List<StatsFlight> journey : byJourney.values()) {
            journey.sort(Comparator.comparing(StatsFlight::date).thenComparing(StatsFlight::userFlightId));
            StatsFlight first = journey.get(0);
            visited.add(new Visit(first.departureCountry(), first.date()));

            for (int i = 0; i < journey.size(); i++) {
                StatsFlight flight = journey.get(i);
                boolean lastOfJourney = i == journey.size() - 1;
                // Flying out of a country you never landed in means you travelled there by other means, so it
                // was visited. Leaving from another airport of the same country is still that one visit.
                if (i > 0 && flight.departureCountry() != null && !flight.departureCountry().equals(journey.get(i - 1).arrivalCountry())) {
                    visited.add(new Visit(flight.departureCountry(), flight.date()));
                }
                // The arrival of the last flight is where the trip ended, so it always counts
                if (lastOfJourney || counts(flight.effectiveStopType())) {
                    visited.add(new Visit(flight.arrivalCountry(), flight.date()));
                } else {
                    passedThrough.add(new Visit(flight.arrivalCountry(), flight.date()));
                }
            }
        }

        List<StatsModels.CountryVisit> visitedCountries = aggregate(visited, countries);
        Set<String> visitedCodes = visitedCountries.stream().map(StatsModels.CountryVisit::code).collect(java.util.stream.Collectors.toSet());
        // A country that was properly visited at some point is not also listed as merely passed through
        List<StatsModels.CountryVisit> passedThroughOnly = aggregate(passedThrough, countries).stream()
                .filter(c -> !visitedCodes.contains(c.code()))
                .toList();
        return new StatsModels.VisitedCountries(visitedCountries, passedThroughOnly);
    }

    private static boolean counts(StopType stopType) {
        return stopType == null || stopType == StopType.STAY || stopType == StopType.LAYOVER_VISITED;
    }

    private static List<StatsModels.CountryVisit> aggregate(List<Visit> visits, Map<String, CountryInfo> countries) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, LocalDate[]> dates = new LinkedHashMap<>();
        for (Visit visit : visits) {
            if (visit.countryCode() == null) {
                continue;
            }
            counts.computeIfAbsent(visit.countryCode(), code -> new int[1])[0]++;
            LocalDate[] range = dates.computeIfAbsent(visit.countryCode(), code -> new LocalDate[]{visit.date(), visit.date()});
            if (visit.date().isBefore(range[0])) {
                range[0] = visit.date();
            }
            if (visit.date().isAfter(range[1])) {
                range[1] = visit.date();
            }
        }
        return counts.entrySet().stream()
                .map(entry -> {
                    CountryInfo info = countries.get(entry.getKey());
                    LocalDate[] range = dates.get(entry.getKey());
                    return new StatsModels.CountryVisit(entry.getKey(),
                            info == null ? entry.getKey() : info.name(), info == null ? "" : info.continent(),
                            entry.getValue()[0], range[0], range[1]);
                })
                .sorted(Comparator.comparingInt(StatsModels.CountryVisit::visitCount).reversed()
                        .thenComparing(StatsModels.CountryVisit::name))
                .toList();
    }

    public record CountryInfo(String name, String continent) {
    }
}
