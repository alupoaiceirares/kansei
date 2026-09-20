package org.kansei.tailwind.stats;

import org.junit.jupiter.api.Test;
import org.kansei.tailwind.model.StopType;

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

class VisitedCountriesCalculatorTest {

    private static final Map<String, VisitedCountriesCalculator.CountryInfo> COUNTRIES = Map.of(
            "RO", new VisitedCountriesCalculator.CountryInfo("Romania", "EU"),
            "TR", new VisitedCountriesCalculator.CountryInfo("Turkey", "AS"),
            "CN", new VisitedCountriesCalculator.CountryInfo("China", "AS"),
            "DE", new VisitedCountriesCalculator.CountryInfo("Germany", "EU"),
            "US", new VisitedCountriesCalculator.CountryInfo("United States", "NA"));

    private static final LocalDate DAY = LocalDate.of(2026, 9, 12);

    private static List<String> codes(List<StatsModels.CountryVisit> visits) {
        return visits.stream().map(StatsModels.CountryVisit::code).toList();
    }

    @Test
    void aLayoverCountryIsPassedThroughNotVisited() {
        // Romania to China via Istanbul, one journey, two flights
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, IST).stop(StopType.LAYOVER).build(),
                flight(1, DAY, IST, PEK).build());

        StatsModels.VisitedCountries result = VisitedCountriesCalculator.calculate(flights, COUNTRIES);

        assertThat(codes(result.visited())).containsExactlyInAnyOrder("RO", "CN");
        assertThat(codes(result.passedThrough())).containsExactly("TR");
    }

    @Test
    void aStayOrAVisitedLayoverCounts() {
        List<StatsFlight> stay = List.of(
                flight(1, DAY, OTP, IST).stop(StopType.STAY).build(),
                flight(1, DAY.plusDays(4), IST, PEK).build());
        List<StatsFlight> visitedLayover = List.of(
                flight(2, DAY, OTP, IST).stop(StopType.LAYOVER_VISITED).build(),
                flight(2, DAY, IST, PEK).build());

        assertThat(codes(VisitedCountriesCalculator.calculate(stay, COUNTRIES).visited())).contains("TR");
        assertThat(codes(VisitedCountriesCalculator.calculate(visitedLayover, COUNTRIES).visited())).contains("TR");
    }

    @Test
    void theSuggestionIsUsedWhenTheUserNeverChose() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, IST).suggestedStop(StopType.LAYOVER).build(),
                flight(1, DAY, IST, PEK).build());

        StatsModels.VisitedCountries result = VisitedCountriesCalculator.calculate(flights, COUNTRIES);

        assertThat(codes(result.passedThrough())).containsExactly("TR");
    }

    @Test
    void anExplicitChoiceBeatsTheSuggestion() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, IST).suggestedStop(StopType.LAYOVER).stop(StopType.LAYOVER_VISITED).build(),
                flight(1, DAY, IST, PEK).build());

        assertThat(codes(VisitedCountriesCalculator.calculate(flights, COUNTRIES).visited())).contains("TR");
    }

    @Test
    void theLastArrivalOfAJourneyAlwaysCountsWhateverItsStopTypeSays() {
        List<StatsFlight> flights = List.of(flight(1, DAY, OTP, PEK).stop(StopType.LAYOVER).build());

        assertThat(codes(VisitedCountriesCalculator.calculate(flights, COUNTRIES).visited())).containsExactlyInAnyOrder("RO", "CN");
    }

    @Test
    void aCountryVisitedOnceIsNeverAlsoListedAsPassedThrough() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, IST).stop(StopType.LAYOVER).build(),
                flight(1, DAY, IST, PEK).build(),
                flight(2, DAY.plusMonths(1), OTP, IST).build());

        StatsModels.VisitedCountries result = VisitedCountriesCalculator.calculate(flights, COUNTRIES);

        assertThat(codes(result.visited())).contains("TR");
        assertThat(codes(result.passedThrough())).isEmpty();
    }

    @Test
    void visitCountsAndFirstLastDatesAddUpAcrossJourneys() {
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, FRA).build(),
                flight(2, DAY.plusMonths(2), FRA, OTP).build(),
                flight(3, DAY.plusMonths(6), OTP, JFK).build());

        StatsModels.CountryVisit romania = VisitedCountriesCalculator.calculate(flights, COUNTRIES).visited().stream()
                .filter(c -> c.code().equals("RO")).findFirst().orElseThrow();

        // departure of journey 1, arrival of journey 2, departure of journey 3
        assertThat(romania.visitCount()).isEqualTo(3);
        assertThat(romania.name()).isEqualTo("Romania");
        assertThat(romania.continent()).isEqualTo("EU");
        assertThat(romania.firstVisit()).isEqualTo(DAY);
        assertThat(romania.lastVisit()).isEqualTo(DAY.plusMonths(6));
    }

    @Test
    void flyingHomeFromAnAirportYouNeverLandedAtStillCountsThatCountry() {
        // Bucharest to Istanbul, overland to Frankfurt, then home: Germany was visited although no flight landed there
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, IST).stop(StopType.STAY).build(),
                flight(1, DAY.plusDays(5), FRA, OTP).build());

        StatsModels.VisitedCountries result = VisitedCountriesCalculator.calculate(flights, COUNTRIES);

        assertThat(codes(result.visited())).containsExactlyInAnyOrder("RO", "TR", "DE");
        assertThat(result.passedThrough()).isEmpty();
    }

    @Test
    void landingAtOneAirportAndLeavingFromAnotherInTheSameCountryIsOneVisit() {
        // Arrive at Istanbul, spend a week in Turkey, fly home from Sabiha Gokcen: one visit, not two
        List<StatsFlight> flights = List.of(
                flight(1, DAY, OTP, IST).stop(StopType.STAY).build(),
                flight(1, DAY.plusDays(7), StatsFlightFixtures.SAW, OTP).build());

        StatsModels.VisitedCountries result = VisitedCountriesCalculator.calculate(flights, COUNTRIES);
        StatsModels.CountryVisit turkey = result.visited().stream().filter(c -> c.code().equals("TR")).findFirst().orElseThrow();

        assertThat(turkey.visitCount()).isEqualTo(1);
        assertThat(codes(result.visited())).containsExactlyInAnyOrder("RO", "TR");
    }

    @Test
    void noFlightsMeansNoCountries() {
        StatsModels.VisitedCountries result = VisitedCountriesCalculator.calculate(List.of(), COUNTRIES);

        assertThat(result.visited()).isEmpty();
        assertThat(result.passedThrough()).isEmpty();
    }
}
