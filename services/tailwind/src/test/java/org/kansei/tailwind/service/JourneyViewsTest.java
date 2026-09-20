package org.kansei.tailwind.service;

import org.junit.jupiter.api.Test;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.model.Flight;
import org.kansei.tailwind.model.StopType;
import org.kansei.tailwind.model.UserFlight;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JourneyViewsTest {

    private static Airport airport(long id, String iata) {
        return Airport.builder().id(id).iata(iata).icao("X" + iata).build();
    }

    private static UserFlight leg(Airport from, Airport to, LocalDate date, Instant departs, Instant arrives) {
        return UserFlight.builder().flight(Flight.builder().departureAirport(from).arrivalAirport(to).flightDate(date)
                .departureScheduledUtc(departs).arrivalScheduledUtc(arrives).build()).build();
    }

    private final Airport otp = airport(1, "OTP");
    private final Airport ist = airport(2, "IST");
    private final Airport pek = airport(3, "PEK");
    private final Airport fra = airport(4, "FRA");
    private final LocalDate day = LocalDate.of(2026, 9, 12);

    @Test
    void sameAirportWithinTwelveHoursIsALayover() {
        UserFlight first = leg(otp, ist, day, Instant.parse("2026-09-12T06:00:00Z"), Instant.parse("2026-09-12T08:00:00Z"));
        UserFlight second = leg(ist, pek, day, Instant.parse("2026-09-12T11:00:00Z"), Instant.parse("2026-09-12T20:00:00Z"));

        assertThat(JourneyViews.suggestStopType(first, second)).isEqualTo(StopType.LAYOVER);
    }

    @Test
    void sameAirportAfterMoreThanTwelveHoursIsAStay() {
        UserFlight first = leg(otp, ist, day, Instant.parse("2026-09-12T06:00:00Z"), Instant.parse("2026-09-12T08:00:00Z"));
        UserFlight thirteenHours = leg(ist, pek, day, Instant.parse("2026-09-12T21:00:00Z"), Instant.parse("2026-09-13T06:00:00Z"));
        UserFlight twelveHours = leg(ist, pek, day, Instant.parse("2026-09-12T20:00:00Z"), Instant.parse("2026-09-13T06:00:00Z"));
        UserFlight twoDays = leg(ist, pek, day.plusDays(2), Instant.parse("2026-09-14T11:00:00Z"), Instant.parse("2026-09-14T20:00:00Z"));

        assertThat(JourneyViews.suggestStopType(first, thirteenHours)).isEqualTo(StopType.STAY);
        assertThat(JourneyViews.suggestStopType(first, twelveHours)).isEqualTo(StopType.LAYOVER);
        assertThat(JourneyViews.suggestStopType(first, twoDays)).isEqualTo(StopType.STAY);
    }

    @Test
    void differentAirportIsAStayEvenWhenClose() {
        UserFlight first = leg(otp, ist, day, Instant.parse("2026-09-12T06:00:00Z"), Instant.parse("2026-09-12T08:00:00Z"));
        UserFlight second = leg(fra, pek, day, Instant.parse("2026-09-12T10:00:00Z"), Instant.parse("2026-09-12T20:00:00Z"));

        assertThat(JourneyViews.suggestStopType(first, second)).isEqualTo(StopType.STAY);
    }

    @Test
    void withoutTimesOnlyTheSameCalendarDayIsALayover() {
        UserFlight first = leg(otp, ist, day, null, null);
        UserFlight sameDay = leg(ist, pek, day, null, null);
        UserFlight nextDay = leg(ist, pek, day.plusDays(1), null, null);

        assertThat(JourneyViews.suggestStopType(first, sameDay)).isEqualTo(StopType.LAYOVER);
        assertThat(JourneyViews.suggestStopType(first, nextDay)).isEqualTo(StopType.STAY);
    }

    @Test
    void anOverlappingNextFlightIsNotALayover() {
        UserFlight first = leg(otp, ist, day, Instant.parse("2026-09-12T06:00:00Z"), Instant.parse("2026-09-12T08:00:00Z"));
        UserFlight before = leg(ist, pek, day, Instant.parse("2026-09-12T07:00:00Z"), Instant.parse("2026-09-12T20:00:00Z"));

        assertThat(JourneyViews.suggestStopType(first, before)).isEqualTo(StopType.STAY);
    }

    @Test
    void autoTitleRunsFromFirstDepartureToLastArrival() {
        UserFlight first = leg(otp, ist, day, null, null);
        UserFlight second = leg(ist, pek, day, null, null);

        assertThat(JourneyViews.autoTitle(List.of(first, second))).isEqualTo("OTP - PEK");
        assertThat(JourneyViews.autoTitle(List.of())).isEqualTo("Empty journey");
    }
}
