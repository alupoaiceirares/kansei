package org.kansei.tailwind.stats;

import org.junit.jupiter.api.Test;
import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.TripReason;

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

/**
 * The records and breakdowns built on what the user recorded per flight, rather than on the flight data.
 */
class ExtraRecordsAndBreakdownsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 12);

    @Test
    void furthestPointIsMeasuredFromTheMostUsedAirport() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).build(),
                flight(2, DAY.plusDays(1), FRA, OTP).build(),
                flight(3, DAY.plusDays(2), OTP, PEK).build());

        StatsModels.FurthestPoint furthest = TravelStatsCalculator.records(flights, Map.of()).furthestPoint();

        assertThat(furthest.homeIata()).isEqualTo("OTP");
        assertThat(furthest.iata()).isEqualTo("PEK");
        assertThat(furthest.distanceFromHomeKm()).isGreaterThan(7000);
    }

    @Test
    void longestGapIsTheWidestStretchBetweenTwoFlights() {
        List<StatsFlight> flights = List.of(
                flight(1, LocalDate.of(2020, 4, 1), OTP, FRA).build(),
                flight(2, LocalDate.of(2021, 6, 1), FRA, OTP).build(),
                flight(3, LocalDate.of(2021, 7, 1), OTP, IST).build());

        StatsModels.LongestGap gap = TravelStatsCalculator.records(flights, Map.of()).longestGap();

        assertThat(gap.from()).isEqualTo(LocalDate.of(2020, 4, 1));
        assertThat(gap.to()).isEqualTo(LocalDate.of(2021, 6, 1));
        assertThat(gap.days()).isEqualTo(426);
    }

    @Test
    void oneFlightHasNoGapToMeasure() {
        List<StatsFlight> flights = List.of(flight(1, DAY, OTP, FRA).build());

        assertThat(TravelStatsCalculator.records(flights, Map.of()).longestGap()).isNull();
    }

    @Test
    void mostAircraftInAJourneyCountsDistinctTypes() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).aircraft(10L, "A320", "Airbus A320", "A320 family").build(),
                flight(1, DAY, FRA, JFK).aircraft(11L, "A346", "Airbus A340-600", "A340").build(),
                flight(1, DAY.plusDays(4), JFK, FRA).aircraft(11L, "A346", "Airbus A340-600", "A340").build(),
                flight(2, DAY, OTP, IST).aircraft(12L, "B738", "Boeing 737-800", "737").build());

        StatsModels.JourneyAircraftRecord record = TravelStatsCalculator.records(flights, Map.of(1L, "Frankfurt run"))
                .mostAircraftInAJourney();

        assertThat(record.title()).isEqualTo("Frankfurt run");
        assertThat(record.aircraftCount()).isEqualTo(2);
        assertThat(record.flightCount()).isEqualTo(3);
    }

    @Test
    void highestCabinIsTheBestOneActuallyFlown() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).cabin(CabinClass.ECONOMY).build(),
                flight(2, DAY.plusDays(1), FRA, JFK).cabin(CabinClass.BUSINESS).build(),
                flight(3, DAY.plusDays(2), JFK, FRA).cabin(CabinClass.PREMIUM_ECONOMY).build());

        StatsModels.CabinRecord cabin = TravelStatsCalculator.records(flights, Map.of()).highestCabin();

        assertThat(cabin.cabinClass()).isEqualTo("BUSINESS");
        assertThat(cabin.flightCount()).isEqualTo(1);
        assertThat(cabin.firstFlight()).isEqualTo(DAY.plusDays(1));
    }

    @Test
    void cabinRecordIsNullWhenNothingWasEverRecorded() {
        List<StatsFlight> flights = List.of(flight(1, DAY, OTP, FRA).build());

        assertThat(TravelStatsCalculator.records(flights, Map.of()).highestCabin()).isNull();
    }

    @Test
    void breakdownsCountOnlyTheFlightsThatCarryTheValue() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).cabin(CabinClass.ECONOMY).reason(TripReason.LEISURE).seat(SeatPosition.WINDOW).build(),
                flight(2, DAY.plusDays(1), FRA, JFK).cabin(CabinClass.ECONOMY).reason(TripReason.BUSINESS).build(),
                flight(3, LocalDate.of(2025, 5, 4), JFK, FRA).build());

        StatsModels.TravelBreakdowns breakdowns = TravelStatsCalculator.breakdowns(flights);

        assertThat(breakdowns.cabinClasses()).containsExactly(new StatsModels.NamedCount("ECONOMY", 2));
        assertThat(breakdowns.reasons()).hasSize(2);
        assertThat(breakdowns.seatPositions()).containsExactly(new StatsModels.NamedCount("WINDOW", 1));
        assertThat(breakdowns.flightsWithCabin()).isEqualTo(2);
        assertThat(breakdowns.flightsWithReason()).isEqualTo(2);
        assertThat(breakdowns.flightsWithSeatPosition()).isEqualTo(1);
    }

    @Test
    void flightsPerYearAreOrderedOldestFirst() {
        List<StatsFlight> flights = List.of(
                flight(1, LocalDate.of(2024, 2, 1), OTP, FRA).distance(1000).build(),
                flight(2, LocalDate.of(2026, 2, 1), FRA, JFK).distance(6000).build(),
                flight(3, LocalDate.of(2026, 6, 1), JFK, FRA).distance(6000).build());

        List<StatsModels.PeriodCount> perYear = TravelStatsCalculator.breakdowns(flights).flightsPerYear();

        assertThat(perYear).hasSize(2);
        assertThat(perYear.get(0).label()).isEqualTo("2024");
        assertThat(perYear.get(1).label()).isEqualTo("2026");
        assertThat(perYear.get(1).count()).isEqualTo(2);
        assertThat(perYear.get(1).distanceKm()).isEqualTo(12000.0);
    }

    @Test
    void emptyLogGivesEmptyBreakdowns() {
        assertThat(TravelStatsCalculator.breakdowns(List.of())).isEqualTo(StatsModels.TravelBreakdowns.EMPTY);
    }
}
