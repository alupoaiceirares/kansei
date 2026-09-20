package org.kansei.tailwind.stats;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns the visible flights into the profile numbers. Everything here is pure, the loader has already decided
 * what the viewer may see.
 */
public final class TravelStatsCalculator {

    private static final String UNKNOWN_VARIANT = "Variant unknown";

    private TravelStatsCalculator() {
    }

    public static StatsModels.TravelStats stats(List<StatsFlight> flights, int countryCount) {
        if (flights.isEmpty()) {
            return StatsModels.TravelStats.EMPTY;
        }
        double distance = 0;
        int minutes = 0;
        int withDuration = 0;
        int cargo = 0;
        double longest = 0;
        Set<Long> airports = new HashSet<>();
        Set<Long> airlines = new HashSet<>();
        Set<String> families = new HashSet<>();
        Set<Long> types = new HashSet<>();

        for (StatsFlight flight : flights) {
            distance += flight.distanceKm();
            longest = Math.max(longest, flight.distanceKm());
            Integer duration = flight.durationMinutes();
            if (duration != null) {
                minutes += duration;
                withDuration++;
            }
            if (flight.cargo()) {
                cargo++;
            }
            airports.add(flight.departureAirportId());
            airports.add(flight.arrivalAirportId());
            airlines.add(flight.airlineId());
            if (flight.aircraftFamily() != null) {
                families.add(flight.aircraftFamily());
            }
            if (flight.aircraftTypeId() != null) {
                types.add(flight.aircraftTypeId());
            }
        }

        return new StatsModels.TravelStats(flights.size(), round(distance), minutes, withDuration, countryCount,
                airports.size(), airlines.size(), families.size(), types.size(),
                round(longest), round(distance / flights.size()), cargo);
    }

    public static List<StatsModels.AirportVisit> airports(List<StatsFlight> flights) {
        Map<Long, Airport> byAirport = new LinkedHashMap<>();
        for (StatsFlight flight : flights) {
            byAirport.computeIfAbsent(flight.departureAirportId(), id -> Airport.ofDeparture(flight)).addDeparture(flight.date());
            byAirport.computeIfAbsent(flight.arrivalAirportId(), id -> Airport.ofArrival(flight)).addArrival(flight.date());
        }
        return byAirport.values().stream()
                .map(Airport::toVisit)
                .sorted(Comparator.comparingInt(StatsModels.AirportVisit::timesUsed).reversed()
                        .thenComparing(StatsModels.AirportVisit::name))
                .toList();
    }

    public static List<StatsModels.AirlineCount> airlines(List<StatsFlight> flights) {
        Map<Long, int[]> counts = new LinkedHashMap<>();
        Map<Long, double[]> distances = new LinkedHashMap<>();
        Map<Long, StatsFlight> firstSeen = new LinkedHashMap<>();
        for (StatsFlight flight : flights) {
            counts.computeIfAbsent(flight.airlineId(), id -> new int[1])[0]++;
            distances.computeIfAbsent(flight.airlineId(), id -> new double[1])[0] += flight.distanceKm();
            firstSeen.putIfAbsent(flight.airlineId(), flight);
        }
        return counts.entrySet().stream()
                .map(entry -> {
                    StatsFlight sample = firstSeen.get(entry.getKey());
                    return new StatsModels.AirlineCount(sample.airlineId(), sample.airlineIcao(), sample.airlineIata(),
                            sample.airlineName(), entry.getValue()[0], round(distances.get(entry.getKey())[0]));
                })
                .sorted(Comparator.comparingInt(StatsModels.AirlineCount::flightCount).reversed()
                        .thenComparing(StatsModels.AirlineCount::name))
                .toList();
    }

    /**
     * One entry per family, each expanding into the variants actually flown. Flights where only the family
     * could be resolved land in a "Variant unknown" entry so the counts still add up.
     */
    public static List<StatsModels.AircraftFamilyCollection> aircraft(List<StatsFlight> flights, Function<String, String> familyPhotoUrl,
                                                                     Function<Long, String> typePhotoUrl) {
        Map<String, List<StatsFlight>> byFamily = new LinkedHashMap<>();
        for (StatsFlight flight : flights) {
            if (flight.aircraftFamily() != null) {
                byFamily.computeIfAbsent(flight.aircraftFamily(), family -> new ArrayList<>()).add(flight);
            }
        }

        List<StatsModels.AircraftFamilyCollection> result = new ArrayList<>();
        byFamily.forEach((family, familyFlights) -> {
            double distance = familyFlights.stream().mapToDouble(StatsFlight::distanceKm).sum();
            LocalDate first = familyFlights.stream().map(StatsFlight::date).min(LocalDate::compareTo).orElseThrow();
            LocalDate last = familyFlights.stream().map(StatsFlight::date).max(LocalDate::compareTo).orElseThrow();
            result.add(new StatsModels.AircraftFamilyCollection(family, familyFlights.size(), round(distance), first, last,
                    familyPhotoUrl.apply(family), variants(familyFlights, typePhotoUrl)));
        });
        result.sort(Comparator.comparingInt(StatsModels.AircraftFamilyCollection::flightCount).reversed()
                .thenComparing(StatsModels.AircraftFamilyCollection::family));
        return result;
    }

    private static List<StatsModels.AircraftVariantCount> variants(List<StatsFlight> familyFlights, Function<Long, String> typePhotoUrl) {
        Map<Long, List<StatsFlight>> byType = new LinkedHashMap<>();
        List<StatsFlight> unknown = new ArrayList<>();
        for (StatsFlight flight : familyFlights) {
            if (flight.aircraftTypeId() == null) {
                unknown.add(flight);
            } else {
                byType.computeIfAbsent(flight.aircraftTypeId(), id -> new ArrayList<>()).add(flight);
            }
        }

        List<StatsModels.AircraftVariantCount> variants = new ArrayList<>();
        byType.forEach((typeId, typeFlights) -> {
            StatsFlight sample = typeFlights.get(0);
            variants.add(new StatsModels.AircraftVariantCount(typeId, sample.aircraftIcaoCode(), sample.aircraftName(),
                    sample.aircraftManufacturer(), sample.aircraftBodyType(), typeFlights.size(),
                    minDate(typeFlights), maxDate(typeFlights), typePhotoUrl.apply(typeId)));
        });
        variants.sort(Comparator.comparingInt(StatsModels.AircraftVariantCount::flightCount).reversed()
                .thenComparing(StatsModels.AircraftVariantCount::name));
        if (!unknown.isEmpty()) {
            // Always last, it is not a real variant
            variants.add(new StatsModels.AircraftVariantCount(null, null, UNKNOWN_VARIANT, null, null, unknown.size(),
                    minDate(unknown), maxDate(unknown), null));
        }
        return variants;
    }

    public static StatsModels.TravelRecords records(List<StatsFlight> flights, Map<Long, String> journeyTitles) {
        if (flights.isEmpty()) {
            return StatsModels.TravelRecords.EMPTY;
        }
        StatsFlight longest = flights.stream().max(Comparator.comparingDouble(StatsFlight::distanceKm)).orElseThrow();
        StatsFlight shortest = flights.stream().min(Comparator.comparingDouble(StatsFlight::distanceKm)).orElseThrow();
        StatsFlight first = flights.stream().min(Comparator.comparing(StatsFlight::date).thenComparing(StatsFlight::userFlightId)).orElseThrow();

        return new StatsModels.TravelRecords(flightRecord(longest), flightRecord(shortest), flightRecord(first),
                longestJourney(flights, journeyTitles), mostFlownRoute(flights),
                topCount(flights, StatsFlight::aircraftFamily), topCount(flights, StatsFlight::airlineName),
                topPeriod(flights, date -> String.format("%d-%02d", date.getYear(), date.getMonthValue())),
                topPeriod(flights, date -> String.valueOf(date.getYear())));
    }

    private static StatsModels.FlightRecord flightRecord(StatsFlight flight) {
        return new StatsModels.FlightRecord(flight.userFlightId(), flight.flightNumber(), flight.date(), round(flight.distanceKm()),
                flight.departureIata(), flight.arrivalIata(), flight.airlineName(), flight.aircraftName());
    }

    private static StatsModels.JourneyRecord longestJourney(List<StatsFlight> flights, Map<Long, String> journeyTitles) {
        Map<Long, List<StatsFlight>> byJourney = new LinkedHashMap<>();
        for (StatsFlight flight : flights) {
            byJourney.computeIfAbsent(flight.journeyId(), id -> new ArrayList<>()).add(flight);
        }
        return byJourney.entrySet().stream()
                .map(entry -> {
                    List<StatsFlight> journeyFlights = entry.getValue();
                    double distance = journeyFlights.stream().mapToDouble(StatsFlight::distanceKm).sum();
                    return new StatsModels.JourneyRecord(entry.getKey(),
                            journeyTitles.getOrDefault(entry.getKey(), autoTitle(journeyFlights)), journeyFlights.size(),
                            round(distance), minDate(journeyFlights), maxDate(journeyFlights));
                })
                .max(Comparator.comparingDouble(StatsModels.JourneyRecord::distanceKm))
                .orElse(null);
    }

    // A route is direction-agnostic, a return trip counts toward the same pair
    private static StatsModels.RouteRecord mostFlownRoute(List<StatsFlight> flights) {
        Map<String, List<StatsFlight>> byRoute = new LinkedHashMap<>();
        for (StatsFlight flight : flights) {
            long low = Math.min(flight.departureAirportId(), flight.arrivalAirportId());
            long high = Math.max(flight.departureAirportId(), flight.arrivalAirportId());
            byRoute.computeIfAbsent(low + "-" + high, key -> new ArrayList<>()).add(flight);
        }
        return byRoute.values().stream()
                .max(Comparator.comparingInt((List<StatsFlight> routeFlights) -> routeFlights.size())
                        .thenComparingDouble(routeFlights -> routeFlights.get(0).distanceKm()))
                .map(routeFlights -> {
                    StatsFlight sample = routeFlights.get(0);
                    return new StatsModels.RouteRecord(sample.departureIata(), sample.arrivalIata(), sample.departureName(),
                            sample.arrivalName(), routeFlights.size(), round(sample.distanceKm()));
                })
                .orElse(null);
    }

    private static StatsModels.NamedCount topCount(List<StatsFlight> flights, Function<StatsFlight, String> name) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (StatsFlight flight : flights) {
            String value = name.apply(flight);
            if (value != null) {
                counts.computeIfAbsent(value, key -> new int[1])[0]++;
            }
        }
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<String, int[]>>comparingInt(entry -> entry.getValue()[0])
                        .thenComparing(entry -> entry.getKey(), Comparator.reverseOrder()))
                .map(entry -> new StatsModels.NamedCount(entry.getKey(), entry.getValue()[0]))
                .orElse(null);
    }

    private static StatsModels.PeriodCount topPeriod(List<StatsFlight> flights, Function<LocalDate, String> label) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, double[]> distances = new LinkedHashMap<>();
        for (StatsFlight flight : flights) {
            String key = label.apply(flight.date());
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            distances.computeIfAbsent(key, k -> new double[1])[0] += flight.distanceKm();
        }
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<String, int[]>>comparingInt(entry -> entry.getValue()[0])
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new StatsModels.PeriodCount(entry.getKey(), entry.getValue()[0], round(distances.get(entry.getKey())[0])))
                .orElse(null);
    }

    // Same rule as the journey view: a trip ending where it started is named after the farthest airport reached
    private static String autoTitle(List<StatsFlight> journeyFlights) {
        StatsFlight first = journeyFlights.get(0);
        StatsFlight last = journeyFlights.get(journeyFlights.size() - 1);
        if (!first.departureAirportId().equals(last.arrivalAirportId())) {
            return code(first.departureIata(), first.departureIcao()) + " - " + code(last.arrivalIata(), last.arrivalIcao());
        }
        StatsFlight farthest = journeyFlights.stream()
                .max(Comparator.comparingDouble(flight -> org.kansei.tailwind.service.GeoDistance.haversineKm(
                        first.departureLatitude(), first.departureLongitude(), flight.arrivalLatitude(), flight.arrivalLongitude())))
                .orElse(last);
        return code(first.departureIata(), first.departureIcao()) + " - " + code(farthest.arrivalIata(), farthest.arrivalIcao());
    }

    private static String code(String iata, String icao) {
        return iata != null ? iata : icao;
    }

    private static LocalDate minDate(List<StatsFlight> flights) {
        return flights.stream().map(StatsFlight::date).min(LocalDate::compareTo).orElseThrow();
    }

    private static LocalDate maxDate(List<StatsFlight> flights) {
        return flights.stream().map(StatsFlight::date).max(LocalDate::compareTo).orElseThrow();
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static final class Airport {
        private final Long id;
        private final String icao;
        private final String iata;
        private final String name;
        private final String city;
        private final String countryCode;
        private final double latitude;
        private final double longitude;
        private int departures;
        private int arrivals;
        private LocalDate first;
        private LocalDate last;

        private Airport(Long id, String icao, String iata, String name, String city, String countryCode, double latitude, double longitude) {
            this.id = id;
            this.icao = icao;
            this.iata = iata;
            this.name = name;
            this.city = city;
            this.countryCode = countryCode;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        static Airport ofDeparture(StatsFlight f) {
            return new Airport(f.departureAirportId(), f.departureIcao(), f.departureIata(), f.departureName(), f.departureCity(),
                    f.departureCountry(), f.departureLatitude(), f.departureLongitude());
        }

        static Airport ofArrival(StatsFlight f) {
            return new Airport(f.arrivalAirportId(), f.arrivalIcao(), f.arrivalIata(), f.arrivalName(), f.arrivalCity(),
                    f.arrivalCountry(), f.arrivalLatitude(), f.arrivalLongitude());
        }

        void addDeparture(LocalDate date) {
            departures++;
            track(date);
        }

        void addArrival(LocalDate date) {
            arrivals++;
            track(date);
        }

        private void track(LocalDate date) {
            if (first == null || date.isBefore(first)) {
                first = date;
            }
            if (last == null || date.isAfter(last)) {
                last = date;
            }
        }

        StatsModels.AirportVisit toVisit() {
            return new StatsModels.AirportVisit(id, icao, iata, name, city, countryCode, latitude, longitude,
                    departures + arrivals, departures, arrivals, first, last);
        }
    }
}
