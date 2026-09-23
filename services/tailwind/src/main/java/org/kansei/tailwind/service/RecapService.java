package org.kansei.tailwind.service;

import org.kansei.tailwind.dto.YearlyRecapResponse;
import org.kansei.tailwind.dto.YearlyRecapResponse.Moment;
import org.kansei.tailwind.dto.YearlyRecapResponse.NamedCode;
import org.kansei.tailwind.dto.YearlyRecapResponse.NamedCount;
import org.kansei.tailwind.model.Country;
import org.kansei.tailwind.repository.CountryRepository;
import org.kansei.tailwind.stats.StatsFlight;
import org.kansei.tailwind.stats.StatsFlightLoader;
import org.kansei.tailwind.stats.StatsModels;
import org.kansei.tailwind.stats.TravelStatsCalculator;
import org.kansei.tailwind.stats.VisitedCountriesCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a user's yearly recap from the same flattened flights the stats use, so upcoming and canceled flights
 * never count. Owner only, private flights included.
 */
@Service
public class RecapService {

    static final double EQUATOR_KM = 40_075;
    static final double MOON_KM = 384_400;

    private final StatsFlightLoader statsFlightLoader;
    private final CountryRepository countryRepository;
    private final OptInGuard optInGuard;

    public RecapService(StatsFlightLoader statsFlightLoader, CountryRepository countryRepository, OptInGuard optInGuard) {
        this.statsFlightLoader = statsFlightLoader;
        this.countryRepository = countryRepository;
        this.optInGuard = optInGuard;
    }

    // Every year with at least one flown flight, newest first
    @Transactional(readOnly = true)
    public List<Integer> years(UUID userId) {
        optInGuard.require(userId);
        return statsFlightLoader.load(userId, userId, StatsModels.Period.ALL_TIME).stream()
                .map(flight -> flight.date().getYear()).distinct().sorted(Comparator.reverseOrder()).toList();
    }

    @Transactional(readOnly = true)
    public YearlyRecapResponse recap(UUID userId, int year) {
        optInGuard.require(userId);
        return build(statsFlightLoader.load(userId, userId, StatsModels.Period.ALL_TIME), year, countryInfo());
    }

    static YearlyRecapResponse build(List<StatsFlight> all, int year, Map<String, VisitedCountriesCalculator.CountryInfo> countries) {
        List<StatsFlight> inYear = all.stream().filter(f -> f.date().getYear() == year).toList();
        StatsModels.VisitedCountries visitedThisYear = VisitedCountriesCalculator.calculate(inYear, countries);
        StatsModels.TravelStats stats = TravelStatsCalculator.stats(inYear, visitedThisYear.visited().size());

        // First ever visit falls inside this year, from the whole log so a return visit is not "new"
        List<NamedCode> newCountries = VisitedCountriesCalculator.calculate(all, countries).visited().stream()
                .filter(visit -> visit.firstVisit().getYear() == year)
                .map(visit -> new NamedCode(visit.code(), visit.name()))
                .toList();

        Map<String, Integer> firstYearOfFamily = new HashMap<>();
        for (StatsFlight flight : all) {
            if (flight.aircraftFamily() != null) {
                firstYearOfFamily.merge(flight.aircraftFamily(), flight.date().getYear(), Math::min);
            }
        }
        List<String> newFamilies = firstYearOfFamily.entrySet().stream().filter(e -> e.getValue() == year).map(Map.Entry::getKey)
                .sorted().toList();

        List<Integer> months = new ArrayList<>(java.util.Collections.nCopies(12, 0));
        for (StatsFlight flight : inYear) {
            months.set(flight.date().getMonthValue() - 1, months.get(flight.date().getMonthValue() - 1) + 1);
        }
        int max = months.stream().mapToInt(Integer::intValue).max().orElse(0);
        Integer busiest = max == 0 ? null : months.indexOf(max) + 1;

        StatsFlight longest = inYear.stream().max(Comparator.comparingDouble(StatsFlight::distanceKm)).orElse(null);
        StatsFlight first = inYear.stream().min(Comparator.comparing(StatsFlight::date).thenComparing(StatsFlight::userFlightId)).orElse(null);

        Map<String, Integer> airlineCounts = new LinkedHashMap<>();
        inYear.forEach(flight -> airlineCounts.merge(flight.airlineName(), 1, Integer::sum));
        NamedCount topAirline = airlineCounts.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> new NamedCount(e.getKey(), e.getValue())).orElse(null);

        double distance = stats.distanceKm();
        return new YearlyRecapResponse(year, stats.flightCount(), distance, round1(distance / EQUATOR_KM), round2(distance / MOON_KM),
                stats.timeInAirMinutes(), stats.flightsWithDuration(), stats.countryCount(), newCountries, stats.airportCount(),
                stats.airlineCount(), newFamilies, months, busiest, moment(longest), moment(first), topAirline);
    }

    private static Moment moment(StatsFlight flight) {
        if (flight == null) {
            return null;
        }
        String route = code(flight.departureIata(), flight.departureIcao()) + " - " + code(flight.arrivalIata(), flight.arrivalIcao());
        String aircraft = flight.aircraftName() != null ? flight.aircraftName() : flight.aircraftFamily();
        return new Moment(flight.userFlightId(), route, flight.date(), flight.distanceKm(), aircraft);
    }

    private static String code(String iata, String icao) {
        return iata != null ? iata : icao;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    Map<String, VisitedCountriesCalculator.CountryInfo> countryInfo() {
        return countryRepository.findAll().stream()
                .collect(Collectors.toMap(Country::getCode, c -> new VisitedCountriesCalculator.CountryInfo(c.getName(), c.getContinent())));
    }
}
