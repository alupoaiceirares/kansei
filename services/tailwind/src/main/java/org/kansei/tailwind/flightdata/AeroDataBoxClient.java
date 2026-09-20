package org.kansei.tailwind.flightdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AeroDataBox through API.Market. The key only ever travels in a request header and is never logged.
 */
@Slf4j
@Component
public class AeroDataBoxClient implements FlightDataClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final String KEY_HEADER = "x-api-market-key";

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public AeroDataBoxClient(
            @Value("${aerodatabox.base-url}") String baseUrl,
            @Value("${aerodatabox.api-key}") String apiKey,
            ObjectMapper objectMapper
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ExternalFlight> fetchByNumber(String flightNumber, LocalDate date) {
        String body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/flights/number/{number}/{date}").build(flightNumber, date.toString()))
                    .header(KEY_HEADER, apiKey)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return List.of();
        } catch (RestClientException ex) {
            // Message only, the exception text never contains the key (it travels in a header)
            throw new FlightDataUnavailableException("Flight data provider call failed: " + ex.getMessage(), ex);
        }
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return parse(body);
    }

    public List<ExternalFlight> parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                throw new FlightDataUnavailableException("Flight data provider returned an unexpected body");
            }
            List<ExternalFlight> flights = new ArrayList<>();
            for (JsonNode element : root) {
                flights.add(toExternal(objectMapper.treeToValue(element, AdbFlight.class), element.toString()));
            }
            return flights;
        } catch (FlightDataUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new FlightDataUnavailableException("Flight data provider returned a body we could not read", ex);
        }
    }

    private ExternalFlight toExternal(AdbFlight f, String rawJson) {
        AdbMovement dep = f.departure() == null ? new AdbMovement(null, null, null, null, null) : f.departure();
        AdbMovement arr = f.arrival() == null ? new AdbMovement(null, null, null, null, null) : f.arrival();
        AdbAirline airline = f.airline();
        AdbAircraft aircraft = f.aircraft();
        return new ExternalFlight(
                f.number() == null ? null : f.number().replaceAll("\\s", "").toUpperCase(Locale.ROOT),
                localDate(dep.scheduledTime() != null ? dep.scheduledTime() : dep.revisedTime()),
                f.status(),
                Boolean.TRUE.equals(f.cargo()),
                airline == null ? null : new ExternalFlight.Airline(airline.name(), blankToNull(airline.iata()), blankToNull(airline.icao())),
                aircraft == null ? null : new ExternalFlight.Aircraft(blankToNull(aircraft.model()), blankToNull(aircraft.reg()), blankToNull(aircraft.modeS())),
                airport(dep.airport()),
                airport(arr.airport()),
                utc(dep.scheduledTime()),
                utc(dep.revisedTime()),
                utc(dep.runwayTime()),
                utc(arr.scheduledTime()),
                // A future flight only has a predicted arrival, treat it as the revised one
                arr.revisedTime() != null ? utc(arr.revisedTime()) : utc(arr.predictedTime()),
                utc(arr.runwayTime()),
                parseUtc(f.lastUpdatedUtc()),
                rawJson);
    }

    private static ExternalFlight.Airport airport(AdbAirport a) {
        if (a == null) {
            return null;
        }
        return new ExternalFlight.Airport(blankToNull(a.icao()), blankToNull(a.iata()), a.name(), blankToNull(a.municipalityName()),
                blankToNull(a.countryCode()), blankToNull(a.timeZone()),
                a.location() == null ? null : a.location().lat(), a.location() == null ? null : a.location().lon());
    }

    private static Instant utc(AdbTime time) {
        return time == null ? null : parseUtc(time.utc());
    }

    // The provider writes "2026-09-12 08:55Z" and "2026-09-12 10:55+02:00", ISO once the space becomes a T
    private static Instant parseUtc(String text) {
        OffsetDateTime parsed = parseOffset(text);
        return parsed == null ? null : parsed.toInstant();
    }

    private static LocalDate localDate(AdbTime time) {
        OffsetDateTime parsed = time == null ? null : parseOffset(time.local());
        return parsed == null ? null : parsed.toLocalDate();
    }

    private static OffsetDateTime parseOffset(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text.trim().replace(' ', 'T'));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdbFlight(String number, String status, @JsonProperty("isCargo") Boolean cargo, AdbAirline airline, AdbAircraft aircraft,
                     AdbMovement departure, AdbMovement arrival, String lastUpdatedUtc) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdbMovement(AdbAirport airport, AdbTime scheduledTime, AdbTime revisedTime, AdbTime runwayTime, AdbTime predictedTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdbTime(String utc, String local) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdbAirport(String icao, String iata, String name, String municipalityName, String countryCode, String timeZone,
                      AdbLocation location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdbLocation(Double lat, Double lon) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdbAirline(String name, String iata, String icao) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdbAircraft(String reg, String modeS, String model) {
    }
}
