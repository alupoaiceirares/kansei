package org.kansei.tailwind.stats;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kansei.tailwind.stats.StatsFlightFixtures.FRA;
import static org.kansei.tailwind.stats.StatsFlightFixtures.IST;
import static org.kansei.tailwind.stats.StatsFlightFixtures.JFK;
import static org.kansei.tailwind.stats.StatsFlightFixtures.OTP;
import static org.kansei.tailwind.stats.StatsFlightFixtures.PEK;
import static org.kansei.tailwind.stats.StatsFlightFixtures.flight;

class TravelStatsCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 12);

    @Test
    void statsCountDistinctAirportsAirlinesAndAircraft() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).distance(1450).times("2026-09-12T06:00:00Z", "2026-09-12T08:00:00Z").build(),
                flight(1, DAY, FRA, JFK).distance(6189).times("2026-09-12T10:00:00Z", "2026-09-12T18:30:00Z")
                        .aircraft(11L, "B744", "Boeing 747-400", "747").build(),
                flight(2, DAY.plusMonths(1), OTP, FRA).distance(1450).airline(2, "Wizz Air").build());

        StatsModels.TravelStats stats = TravelStatsCalculator.stats(flights, 3);

        assertThat(stats.flightCount()).isEqualTo(3);
        assertThat(stats.distanceKm()).isEqualTo(9089.0);
        assertThat(stats.timeInAirMinutes()).isEqualTo(120 + 510);
        assertThat(stats.flightsWithDuration()).isEqualTo(2);
        assertThat(stats.airportCount()).isEqualTo(3);
        assertThat(stats.airlineCount()).isEqualTo(2);
        assertThat(stats.aircraftFamilyCount()).isEqualTo(2);
        assertThat(stats.aircraftTypeCount()).isEqualTo(2);
        assertThat(stats.countryCount()).isEqualTo(3);
        assertThat(stats.longestFlightKm()).isEqualTo(6189.0);
        assertThat(stats.averageFlightKm()).isEqualTo(3029.67);
        assertThat(stats.cargoFlightCount()).isZero();
    }

    @Test
    void flightsWithoutUsableTimesAreLeftOutOfTimeInAirOnly() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).times("2026-09-12T06:00:00Z", "2026-09-12T08:00:00Z").build(),
                flight(1, DAY, FRA, OTP).build(),
                // an arrival before its departure is nonsense, it must not subtract time
                flight(2, DAY, OTP, FRA).times("2026-09-12T10:00:00Z", "2026-09-12T09:00:00Z").build());

        StatsModels.TravelStats stats = TravelStatsCalculator.stats(flights, 2);

        assertThat(stats.flightCount()).isEqualTo(3);
        assertThat(stats.timeInAirMinutes()).isEqualTo(120);
        assertThat(stats.flightsWithDuration()).isEqualTo(1);
    }

    @Test
    void emptyStatsAreZeroNotNull() {
        StatsModels.TravelStats stats = TravelStatsCalculator.stats(List.of(), 0);

        assertThat(stats.flightCount()).isZero();
        assertThat(stats.distanceKm()).isZero();
        assertThat(stats.longestFlightKm()).isNull();
        assertThat(stats.averageFlightKm()).isNull();
    }

    @Test
    void cargoFlightsAreCountedSeparatelyButStillCount() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).build(),
                flight(2, DAY, FRA, JFK).cargo().build());

        StatsModels.TravelStats stats = TravelStatsCalculator.stats(flights, 3);

        assertThat(stats.flightCount()).isEqualTo(2);
        assertThat(stats.cargoFlightCount()).isEqualTo(1);
    }

    @Test
    void airportsCountDeparturesAndArrivalsSeparatelyAndTrackTheirDates() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).build(),
                flight(2, DAY.plusMonths(1), FRA, OTP).build(),
                flight(3, DAY.plusMonths(2), OTP, JFK).build());

        List<StatsModels.AirportVisit> airports = TravelStatsCalculator.airports(flights);

        StatsModels.AirportVisit otp = airports.stream().filter(a -> "OTP".equals(a.iata())).findFirst().orElseThrow();
        assertThat(otp.timesUsed()).isEqualTo(3);
        assertThat(otp.departures()).isEqualTo(2);
        assertThat(otp.arrivals()).isEqualTo(1);
        assertThat(otp.firstVisit()).isEqualTo(DAY);
        assertThat(otp.lastVisit()).isEqualTo(DAY.plusMonths(2));
        assertThat(otp.latitude()).isEqualTo(44.57);
        // most used first
        assertThat(airports.get(0).iata()).isEqualTo("OTP");
    }

    @Test
    void aircraftAreGroupedByFamilyThenExpandedIntoVariants() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).aircraft(10L, "A346", "Airbus A340-600", "A340").build(),
                flight(2, DAY.plusDays(1), FRA, OTP).aircraft(10L, "A346", "Airbus A340-600", "A340").build(),
                flight(3, DAY.plusDays(2), OTP, FRA).aircraft(11L, "A343", "Airbus A340-300", "A340").build(),
                flight(4, DAY.plusDays(3), FRA, OTP).familyOnly("A340").build(),
                flight(5, DAY.plusDays(4), OTP, JFK).aircraft(12L, "B744", "Boeing 747-400", "747").build());

        List<StatsModels.AircraftFamilyCollection> collection = TravelStatsCalculator.aircraft(flights,
                family -> "/photo?family=" + family, typeId -> "/photo/" + typeId);

        assertThat(collection).hasSize(2);
        StatsModels.AircraftFamilyCollection a340 = collection.get(0);
        assertThat(a340.family()).isEqualTo("A340");
        assertThat(a340.flightCount()).isEqualTo(4);
        assertThat(a340.firstFlight()).isEqualTo(DAY);
        assertThat(a340.lastFlight()).isEqualTo(DAY.plusDays(3));
        assertThat(a340.photoUrl()).isEqualTo("/photo?family=A340");

        assertThat(a340.variants()).extracting(StatsModels.AircraftVariantCount::name)
                .containsExactly("Airbus A340-600", "Airbus A340-300", "Variant unknown");
        assertThat(a340.variants()).extracting(StatsModels.AircraftVariantCount::flightCount).containsExactly(2, 1, 1);
        // the variant counts add up to the family count
        assertThat(a340.variants().stream().mapToInt(StatsModels.AircraftVariantCount::flightCount).sum()).isEqualTo(a340.flightCount());
        StatsModels.AircraftVariantCount unknown = a340.variants().get(2);
        assertThat(unknown.aircraftTypeId()).isNull();
        assertThat(unknown.photoUrl()).isNull();
        assertThat(a340.variants().get(0).photoUrl()).isEqualTo("/photo/10");
    }

    @Test
    void aFlightWithNoResolvedAircraftIsLeftOutOfTheCollection() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).noAircraft().build(),
                flight(2, DAY, FRA, OTP).aircraft(10L, "A346", "Airbus A340-600", "A340").build());

        List<StatsModels.AircraftFamilyCollection> collection = TravelStatsCalculator.aircraft(flights, f -> "f", t -> "t");

        assertThat(collection).hasSize(1);
        assertThat(collection.get(0).flightCount()).isEqualTo(1);
    }

    @Test
    void airlinesAreCountedAndSortedByUse() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).distance(1450).airline(1, "Lufthansa").build(),
                flight(2, DAY, FRA, OTP).distance(1450).airline(1, "Lufthansa").build(),
                flight(3, DAY, OTP, IST).distance(800).airline(2, "Wizz Air").build());

        List<StatsModels.AirlineCount> airlines = TravelStatsCalculator.airlines(flights);

        assertThat(airlines).hasSize(2);
        assertThat(airlines.get(0).name()).isEqualTo("Lufthansa");
        assertThat(airlines.get(0).flightCount()).isEqualTo(2);
        assertThat(airlines.get(0).distanceKm()).isEqualTo(2900.0);
    }

    @Test
    void recordsPickTheRightFlightsJourneysAndPeriods() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).distance(1450).build(),
                flight(1, DAY, FRA, JFK).distance(6189).build(),
                flight(2, DAY.plusMonths(1), OTP, IST).distance(800).build(),
                flight(3, DAY.plusYears(1), IST, OTP).distance(800).build());

        StatsModels.TravelRecords records = TravelStatsCalculator.records(flights, Map.of(1L, "Big trip"));

        assertThat(records.longestFlight().distanceKm()).isEqualTo(6189.0);
        assertThat(records.longestFlight().arrivalIata()).isEqualTo("JFK");
        assertThat(records.shortestFlight().distanceKm()).isEqualTo(800.0);
        assertThat(records.firstFlight().date()).isEqualTo(DAY);
        assertThat(records.longestJourney().journeyId()).isEqualTo(1L);
        assertThat(records.longestJourney().title()).isEqualTo("Big trip");
        assertThat(records.longestJourney().flightCount()).isEqualTo(2);
        assertThat(records.longestJourney().distanceKm()).isEqualTo(7639.0);
        // OTP to IST and back is the same route
        assertThat(records.mostFlownRoute().flightCount()).isEqualTo(2);
        assertThat(records.busiestMonth().label()).isEqualTo("2026-09");
        assertThat(records.busiestMonth().count()).isEqualTo(2);
        assertThat(records.biggestYear().label()).isEqualTo("2026");
        assertThat(records.biggestYear().count()).isEqualTo(3);
        assertThat(records.mostFlownAirline().name()).isEqualTo("Lufthansa");
        assertThat(records.mostFlownAircraftFamily().name()).isEqualTo("A340");
    }

    @Test
    void aJourneyWithoutATitleFallsBackToItsRoute() {
        List<StatsFlight> flights = List.of(flight(7, DAY, OTP, FRA).build());

        StatsModels.TravelRecords records = TravelStatsCalculator.records(flights, Map.of());

        assertThat(records.longestJourney().title()).isEqualTo("OTP - FRA");
    }

    @Test
    void aRoundTripIsNamedAfterTheFarthestAirportReached() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, IST).build(),
                flight(1, DAY.plusDays(3), IST, PEK).build(),
                flight(1, DAY.plusDays(10), PEK, OTP).build());

        StatsModels.TravelRecords records = TravelStatsCalculator.records(flights, Map.of());

        assertThat(records.longestJourney().title()).isEqualTo("OTP - PEK");
        assertThat(records.longestJourney().flightCount()).isEqualTo(3);
    }

    @Test
    void aReturnFromAnotherAirportStillCountsEveryFlightAndItsDistance() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, IST).distance(900).build(),
                flight(1, DAY.plusDays(5), FRA, OTP).distance(1450).build());

        StatsModels.TravelStats stats = TravelStatsCalculator.stats(flights, 3);
        StatsModels.TravelRecords records = TravelStatsCalculator.records(flights, Map.of());

        assertThat(stats.flightCount()).isEqualTo(2);
        assertThat(stats.distanceKm()).isEqualTo(2350.0);
        assertThat(stats.airportCount()).isEqualTo(3);
        assertThat(records.longestJourney().distanceKm()).isEqualTo(2350.0);
        // the outbound and the return are different routes, neither is flown twice
        assertThat(records.mostFlownRoute().flightCount()).isEqualTo(1);
    }

    @Test
    void noFlightsMeansEveryRecordIsNull() {
        StatsModels.TravelRecords records = TravelStatsCalculator.records(List.of(), Map.of());

        assertThat(records.longestFlight()).isNull();
        assertThat(records.busiestMonth()).isNull();
        assertThat(records.mostFlownRoute()).isNull();
    }

    @Test
    void recordsIgnoreAnAircraftThatCouldNotBeResolved() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).noAircraft().build(),
                flight(2, DAY, FRA, PEK).noAircraft().build(),
                flight(3, DAY, OTP, IST).aircraft(10L, "A346", "Airbus A340-600", "A340").build());

        StatsModels.TravelRecords records = TravelStatsCalculator.records(flights, Map.of());

        assertThat(records.mostFlownAircraftFamily().name()).isEqualTo("A340");
        assertThat(records.mostFlownAircraftFamily().count()).isEqualTo(1);
    }
}
