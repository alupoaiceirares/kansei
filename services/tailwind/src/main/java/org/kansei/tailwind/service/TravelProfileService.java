package org.kansei.tailwind.service;

import org.kansei.tailwind.model.Country;
import org.kansei.tailwind.model.Journey;
import org.kansei.tailwind.repository.CountryRepository;
import org.kansei.tailwind.repository.JourneyRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.stats.StatsFlight;
import org.kansei.tailwind.stats.StatsFlightLoader;
import org.kansei.tailwind.stats.StatsModels;
import org.kansei.tailwind.stats.TravelStatsCalculator;
import org.kansei.tailwind.stats.VisitedCountriesCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Backs the travel profile query. Each resolver calls its own method here, so a query that asks only for stats
 * never computes the collections.
 */
@Service
public class TravelProfileService {

    private static final String TYPE_PHOTO_PATH = "/tailwind/aircraft-types/%d/photo";
    private static final String FAMILY_PHOTO_PATH = "/tailwind/aircraft-families/photo?name=%s";

    private final StatsFlightLoader statsFlightLoader;
    private final CountryRepository countryRepository;
    private final JourneyRepository journeyRepository;
    private final TailwindUserRepository tailwindUserRepository;
    private final OptInGuard optInGuard;

    public TravelProfileService(StatsFlightLoader statsFlightLoader, CountryRepository countryRepository,
                                JourneyRepository journeyRepository, TailwindUserRepository tailwindUserRepository,
                                OptInGuard optInGuard) {
        this.statsFlightLoader = statsFlightLoader;
        this.countryRepository = countryRepository;
        this.journeyRepository = journeyRepository;
        this.tailwindUserRepository = tailwindUserRepository;
        this.optInGuard = optInGuard;
    }

    public void requireAccess(UUID viewerId) {
        optInGuard.require(viewerId);
    }

    // A profile only exists for someone who opted in, and saying so is not a leak: opting in is not private
    public void requireProfileExists(UUID ownerId) {
        if (!tailwindUserRepository.existsById(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That user has not opted into tailwind");
        }
    }

    @Transactional(readOnly = true)
    public StatsModels.TravelStats stats(UUID viewerId, UUID ownerId, StatsModels.Period period) {
        List<StatsFlight> flights = load(viewerId, ownerId, period);
        int countryCount = VisitedCountriesCalculator.calculate(flights, countryInfo()).visited().size();
        return TravelStatsCalculator.stats(flights, countryCount);
    }

    @Transactional(readOnly = true)
    public StatsModels.VisitedCountries countries(UUID viewerId, UUID ownerId, StatsModels.Period period) {
        return VisitedCountriesCalculator.calculate(load(viewerId, ownerId, period), countryInfo());
    }

    @Transactional(readOnly = true)
    public List<StatsModels.AirportVisit> airports(UUID viewerId, UUID ownerId, StatsModels.Period period) {
        return TravelStatsCalculator.airports(load(viewerId, ownerId, period));
    }

    @Transactional(readOnly = true)
    public List<StatsModels.AirlineCount> airlines(UUID viewerId, UUID ownerId, StatsModels.Period period) {
        return TravelStatsCalculator.airlines(load(viewerId, ownerId, period));
    }

    @Transactional(readOnly = true)
    public List<StatsModels.AircraftFamilyCollection> aircraft(UUID viewerId, UUID ownerId, StatsModels.Period period) {
        return TravelStatsCalculator.aircraft(load(viewerId, ownerId, period), familyPhotoUrl(), typePhotoUrl());
    }

    @Transactional(readOnly = true)
    public StatsModels.TravelRecords records(UUID viewerId, UUID ownerId, StatsModels.Period period) {
        List<StatsFlight> flights = load(viewerId, ownerId, period);
        Map<Long, String> titles = journeyRepository.findByUserIdOrderByCreatedAtDescIdDesc(ownerId).stream()
                .filter(journey -> journey.getTitle() != null && !journey.getTitle().isBlank())
                .collect(Collectors.toMap(Journey::getId, Journey::getTitle));
        return TravelStatsCalculator.records(flights, titles);
    }

    private List<StatsFlight> load(UUID viewerId, UUID ownerId, StatsModels.Period period) {
        return statsFlightLoader.load(viewerId, ownerId, period == null ? StatsModels.Period.ALL_TIME : period);
    }

    private Map<String, VisitedCountriesCalculator.CountryInfo> countryInfo() {
        return countryRepository.findAll().stream()
                .collect(Collectors.toMap(Country::getCode, c -> new VisitedCountriesCalculator.CountryInfo(c.getName(), c.getContinent())));
    }

    private static Function<String, String> familyPhotoUrl() {
        return family -> String.format(FAMILY_PHOTO_PATH, UriUtils.encodeQueryParam(family, StandardCharsets.UTF_8));
    }

    private static Function<Long, String> typePhotoUrl() {
        return typeId -> String.format(TYPE_PHOTO_PATH, typeId);
    }
}
