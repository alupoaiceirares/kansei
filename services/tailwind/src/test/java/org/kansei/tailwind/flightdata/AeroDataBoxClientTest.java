package org.kansei.tailwind.flightdata;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Maps real AeroDataBox responses (LH400 Frankfurt to New York, one past and one scheduled flight).
 */
class AeroDataBoxClientTest {

    private final AeroDataBoxClient client = new AeroDataBoxClient("http://localhost", "unused", JsonMapper.builder().build());

    private static String fixture(String name) throws IOException {
        return new String(new ClassPathResource("aerodatabox/" + name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void mapsAFlownFlight() throws IOException {
        List<ExternalFlight> flights = client.parse(fixture("lh400-past.json"));

        assertThat(flights).hasSize(1);
        ExternalFlight f = flights.get(0);
        assertThat(f.flightNumber()).isEqualTo("LH400");
        assertThat(f.flightDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(f.status()).isEqualTo("Arrived");
        assertThat(f.cargo()).isFalse();
        assertThat(f.airline()).isEqualTo(new ExternalFlight.Airline("Lufthansa", "LH", "DLH"));
        assertThat(f.aircraft()).isEqualTo(new ExternalFlight.Aircraft("Airbus A340", "D-AIHX", "3C6518"));
        assertThat(f.departure().icao()).isEqualTo("EDDF");
        assertThat(f.departure().iata()).isEqualTo("FRA");
        assertThat(f.departure().timeZone()).isEqualTo("Europe/Berlin");
        assertThat(f.arrival().icao()).isEqualTo("KJFK");
        assertThat(f.arrival().latitude()).isEqualTo(40.6398);
        assertThat(f.departureScheduled()).isEqualTo(Instant.parse("2026-09-12T08:55:00Z"));
        assertThat(f.departureActual()).isEqualTo(Instant.parse("2026-09-12T09:35:00Z"));
        assertThat(f.arrivalScheduled()).isEqualTo(Instant.parse("2026-09-12T17:35:00Z"));
        assertThat(f.arrivalRevised()).isEqualTo(Instant.parse("2026-09-12T17:39:00Z"));
        assertThat(f.arrivalActual()).isEqualTo(Instant.parse("2026-09-12T17:36:00Z"));
        assertThat(f.lastUpdated()).isEqualTo(Instant.parse("2026-09-12T17:39:00Z"));
        assertThat(f.rawJson()).contains("\"number\":\"LH 400\"");
    }

    @Test
    void mapsAScheduledFlightWithoutRegistrationAndUsesThePredictedArrival() throws IOException {
        ExternalFlight f = client.parse(fixture("lh400-future.json")).get(0);

        assertThat(f.status()).isEqualTo("Expected");
        assertThat(f.aircraft().model()).isEqualTo("Boeing 747-400");
        assertThat(f.aircraft().registration()).isNull();
        assertThat(f.aircraft().modeS()).isNull();
        assertThat(f.flightDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(f.departureActual()).isNull();
        assertThat(f.arrivalRevised()).isEqualTo(Instant.parse("2026-11-19T18:21:00Z"));
    }

    @Test
    void anEmptyArrayIsNoFlights() {
        assertThat(client.parse("[]")).isEmpty();
    }

    @Test
    void aNonArrayBodyIsAProviderFault() {
        assertThatThrownBy(() -> client.parse("{\"message\":\"nope\"}")).isInstanceOf(FlightDataUnavailableException.class);
    }

    @Test
    void garbageIsAProviderFault() {
        assertThatThrownBy(() -> client.parse("<html>")).isInstanceOf(FlightDataUnavailableException.class);
    }
}
