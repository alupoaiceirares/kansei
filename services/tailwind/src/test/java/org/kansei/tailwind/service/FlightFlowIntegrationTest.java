package org.kansei.tailwind.service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.tailwind.aircraft.AircraftResolver;
import org.kansei.tailwind.flightdata.AeroDataBoxClient;
import org.kansei.tailwind.flightdata.ExternalFlight;
import org.kansei.tailwind.flightdata.FlightDataClient;
import org.kansei.tailwind.flightdata.FlightDataUnavailableException;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.AircraftModelAliasRepository;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole flight flow over HTTP against a real Postgres: lookup and storage, dedup, aircraft resolution,
 * confirm with visibility and cargo, journeys, stop types, manual entry and cleanup. Only the flight data
 * provider, Redis guards, RabbitMQ and shieldwall are replaced.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Import(FlightFlowIntegrationTest.FixedClock.class)
class FlightFlowIntegrationTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("SERVER_PORT", () -> "0");
        registry.add("LOG_FILE", () -> "target/test-logs/tailwind.json");
        registry.add("REDIS_HOST", () -> "localhost");
        registry.add("REDIS_PORT", () -> "6379");
        registry.add("REDIS_PASSWORD", () -> "test");
        registry.add("SHIELDWALL_INTERNAL_URI", () -> "http://localhost:1");
        registry.add("INTERNAL_SERVICE_SECRET", () -> "test");
        registry.add("RABBITMQ_HOST", () -> "localhost");
        registry.add("RABBITMQ_PORT", () -> "5672");
        registry.add("RABBITMQ_USERNAME", () -> "test");
        registry.add("RABBITMQ_PASSWORD", () -> "test");
        registry.add("AUDIT_FALLBACK_FILE", () -> "target/test-logs/audit-fallback.jsonl");
        registry.add("STORAGE_ROOT", () -> "target/test-storage");
        registry.add("AERODATABOX_API_KEY", () -> "test");
        registry.add("AERODATABOX_MONTHLY_UNIT_CAP", () -> "600");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TailwindUserRepository tailwindUserRepository;
    @Autowired
    private AirportRepository airportRepository;
    @Autowired
    private AirlineRepository airlineRepository;
    @Autowired
    private AircraftTypeRepository aircraftTypeRepository;
    @Autowired
    private AircraftModelAliasRepository aliasRepository;
    @Autowired
    private AircraftResolver aircraftResolver;
    @Autowired
    private UserDataCleaner userDataCleaner;

    @MockitoBean
    private FlightDataClient flightDataClient;
    @MockitoBean
    private LookupQuotaGuard quotaGuard;
    @MockitoBean
    private LookupRateLimiter rateLimiter;
    @MockitoBean
    private LookupMissCache missCache;
    @MockitoBean
    private AuditPublisher auditPublisher;
    @MockitoBean
    private ShieldwallUserClient shieldwallUserClient;

    private static final LocalDate PAST_DAY = LocalDate.of(2026, 9, 12);

    private UUID user;

    @BeforeEach
    void freshUser() {
        jdbc.execute("TRUNCATE user_flights, journeys, flights, tailwind_users RESTART IDENTITY CASCADE");
        user = optedIn();
    }

    private UUID optedIn() {
        UUID id = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(id).joinedAt(Instant.now()).build());
        return id;
    }

    private static String fixture(String name) throws IOException {
        return new String(new ClassPathResource("aerodatabox/" + name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private List<ExternalFlight> external(String json) {
        return new AeroDataBoxClient("http://localhost", "unused", objectMapper).parse(json);
    }

    private void providerHasPastLh400() throws IOException {
        when(flightDataClient.fetchByNumber("LH400", PAST_DAY)).thenReturn(external(fixture("lh400-past.json")));
    }

    private static MockHttpServletRequestBuilder as(UUID who, MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", who.toString());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions lookup(UUID who, String number, LocalDate date) throws Exception {
        return mockMvc.perform(as(who, get("/tailwind/flights/lookup").param("flightNumber", number).param("date", date.toString())));
    }

    private long lookedUpFlightId() throws Exception {
        providerHasPastLh400();
        String body = lookup(user, "LH400", PAST_DAY).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.flights[0].flight.id")).longValue();
    }

    private JsonPathReader add(UUID who, String body, HttpStatus expected) throws Exception {
        String response = mockMvc.perform(json(as(who, post("/tailwind/flights")), body))
                .andExpect(status().is(expected.value())).andReturn().getResponse().getContentAsString();
        return new JsonPathReader(response);
    }

    private record JsonPathReader(String body) {
        long number(String path) {
            return ((Number) JsonPath.read(body, path)).longValue();
        }
    }

    // ---- lookup

    @Test
    void lookupStoresTheFlightResolvesTheAircraftAndServesTheSecondLookupFromTheDatabase() throws Exception {
        providerHasPastLh400();

        lookup(user, "lh 400", PAST_DAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flights.length()").value(1))
                .andExpect(jsonPath("$.flights[0].flight.flightNumber").value("LH400"))
                .andExpect(jsonPath("$.flights[0].flight.airline.icao").value("DLH"))
                .andExpect(jsonPath("$.flights[0].flight.departureAirport.iata").value("FRA"))
                .andExpect(jsonPath("$.flights[0].flight.arrivalAirport.iata").value("JFK"))
                .andExpect(jsonPath("$.flights[0].flight.aircraft.family").value("A340"))
                .andExpect(jsonPath("$.flights[0].flight.aircraft.typeIcao").value("A346"))
                .andExpect(jsonPath("$.flights[0].flight.aircraft.modelRaw").value("Airbus A340"))
                .andExpect(jsonPath("$.flights[0].flight.aircraft.registration").value("D-AIHX"))
                .andExpect(jsonPath("$.flights[0].flight.cargo").value(false))
                .andExpect(jsonPath("$.flights[0].flight.upcoming").value(false))
                .andExpect(jsonPath("$.flights[0].flight.distanceKm").value(org.hamcrest.Matchers.closeTo(6189, 15)))
                .andExpect(jsonPath("$.flights[0].alreadyInLog").value(false))
                .andExpect(jsonPath("$.flights[0].flight.apiPayload").doesNotExist());

        lookup(optedIn(), "LH400", PAST_DAY).andExpect(status().isOk());

        verify(flightDataClient, times(1)).fetchByNumber(any(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flights", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT api_payload->>'number' FROM flights", String.class)).isEqualTo("LH 400");
    }

    @Test
    void aScheduledFlightWithoutRegistrationResolvesFromTheModelString() throws Exception {
        when(flightDataClient.fetchByNumber("LH400", LocalDate.of(2026, 11, 19))).thenReturn(external(fixture("lh400-future.json")));

        lookup(user, "LH400", LocalDate.of(2026, 11, 19))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flights[0].flight.status").value("Expected"))
                .andExpect(jsonPath("$.flights[0].flight.upcoming").value(true))
                .andExpect(jsonPath("$.flights[0].flight.aircraft.typeIcao").value("B744"))
                .andExpect(jsonPath("$.flights[0].flight.aircraft.family").value("747"))
                .andExpect(jsonPath("$.flights[0].flight.aircraft.registration").doesNotExist());
    }

    @Test
    void unknownAirportsAndAirlinesInTheResponseAreStoredForNextTime() throws Exception {
        String json = fixture("lh400-past.json")
                .replace("\"number\": \"LH 400\"", "\"number\": \"ZZ 400\"")
                .replace("\"icao\": \"DLH\"", "\"icao\": \"XQX\"")
                .replace("\"iata\": \"LH\"", "\"iata\": \"ZZ\"")
                .replace("\"name\": \"Lufthansa\"", "\"name\": \"Zed Air\"")
                .replace("\"icao\": \"KJFK\"", "\"icao\": \"LRZZ\"")
                .replace("\"iata\": \"JFK\"", "\"iata\": \"ZZR\"")
                .replace("\"countryCode\": \"US\"", "\"countryCode\": \"RO\"");
        when(flightDataClient.fetchByNumber("ZZ400", PAST_DAY)).thenReturn(external(json));

        lookup(user, "ZZ400", PAST_DAY).andExpect(status().isOk()).andExpect(jsonPath("$.flights[0].flight.airline.name").value("Zed Air"));

        assertThat(airportRepository.findByIcao("LRZZ")).hasValueSatisfying(a -> {
            assertThat(a.getAirportType()).isEqualTo("unknown");
            assertThat(a.getCountryCode()).isEqualTo("RO");
        });
        assertThat(airlineRepository.findByIcao("XQX")).isPresent();
    }

    @Test
    void lookupRefusesBadInputBeforeSpendingAnything() throws Exception {
        lookup(user, "X", PAST_DAY).andExpect(status().isBadRequest());
        lookup(user, "LH400", LocalDate.of(2020, 1, 1)).andExpect(status().isBadRequest());
        lookup(user, "LH400", LocalDate.of(2030, 1, 1)).andExpect(status().isBadRequest());

        verify(flightDataClient, never()).fetchByNumber(any(), any());
        verify(quotaGuard, never()).reserveLookup();
    }

    @Test
    void lookupNeedsAnOptedInUser() throws Exception {
        lookup(UUID.randomUUID(), "LH400", PAST_DAY).andExpect(status().isForbidden());

        verify(flightDataClient, never()).fetchByNumber(any(), any());
    }

    @Test
    void noSuchFlightIsRememberedAsAMissAndProviderFaultsAreABadGateway() throws Exception {
        when(flightDataClient.fetchByNumber("LH999", PAST_DAY)).thenReturn(List.of());
        when(flightDataClient.fetchByNumber("LH998", PAST_DAY)).thenThrow(new FlightDataUnavailableException("down"));

        lookup(user, "LH999", PAST_DAY).andExpect(status().isNotFound());
        verify(missCache).remember("LH999", PAST_DAY);

        lookup(user, "LH998", PAST_DAY).andExpect(status().isBadGateway());
        verify(missCache, never()).remember(eq("LH998"), any());
    }

    @Test
    void aKnownMissAndTheGuardsStopTheLookupBeforeTheProvider() throws Exception {
        when(missCache.isKnownMiss("LH997", PAST_DAY)).thenReturn(true);
        lookup(user, "LH997", PAST_DAY).andExpect(status().isNotFound());

        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "budget")).when(quotaGuard).reserveLookup();
        lookup(user, "LH996", PAST_DAY).andExpect(status().isServiceUnavailable());

        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "slow down")).when(rateLimiter).checkAndCount(any());
        lookup(user, "LH995", PAST_DAY).andExpect(status().isTooManyRequests());

        verify(flightDataClient, never()).fetchByNumber(any(), any());
    }

    // ---- confirm, visibility, cargo

    @Test
    void confirmingAFlightUsesTheDefaultVisibilityAndCreatesAJourneyAndRefusesADuplicate() throws Exception {
        long flightId = lookedUpFlightId();

        JsonPathReader added = add(user, "{\"flightId\":" + flightId + "}", HttpStatus.CREATED);

        assertThat(JsonPath.<String>read(added.body(), "$.visibility")).isEqualTo("PUBLIC");
        assertThat(added.number("$.journeyId")).isPositive();
        lookup(user, "LH400", PAST_DAY).andExpect(jsonPath("$.flights[0].alreadyInLog").value(true));
        add(user, "{\"flightId\":" + flightId + "}", HttpStatus.CONFLICT);
        mockMvc.perform(as(user, get("/tailwind/journeys"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("FRA - JFK"))
                .andExpect(jsonPath("$[0].titleIsCustom").value(false));
    }

    @Test
    void anExplicitVisibilityWinsOverTheDefault() throws Exception {
        long flightId = lookedUpFlightId();

        JsonPathReader added = add(user, "{\"flightId\":" + flightId + ",\"visibility\":\"FRIENDS\",\"seat\":\"12A\",\"seatPosition\":\"WINDOW\","
                + "\"cabinClass\":\"BUSINESS\",\"reason\":\"LEISURE\",\"notes\":\"long one\"}", HttpStatus.CREATED);

        assertThat(JsonPath.<String>read(added.body(), "$.visibility")).isEqualTo("FRIENDS");
        assertThat(JsonPath.<String>read(added.body(), "$.seat")).isEqualTo("12A");
        assertThat(JsonPath.<String>read(added.body(), "$.cabinClass")).isEqualTo("BUSINESS");
    }

    @Test
    void aCargoFlightNeedsAnExplicitConfirmation() throws Exception {
        String cargoJson = fixture("lh400-past.json").replace("\"isCargo\": false", "\"isCargo\": true").replace("\"number\": \"LH 400\"", "\"number\": \"LH 401\"");
        when(flightDataClient.fetchByNumber("LH401", PAST_DAY)).thenReturn(external(cargoJson));
        String body = lookup(user, "LH401", PAST_DAY).andExpect(status().isOk()).andExpect(jsonPath("$.flights[0].flight.cargo").value(true))
                .andReturn().getResponse().getContentAsString();
        long flightId = ((Number) JsonPath.read(body, "$.flights[0].flight.id")).longValue();

        add(user, "{\"flightId\":" + flightId + "}", HttpStatus.CONFLICT);
        add(user, "{\"flightId\":" + flightId + ",\"confirmCargo\":true}", HttpStatus.CREATED);
    }

    @Test
    void addingNeedsAnOptedInUserAndAnExistingFlight() throws Exception {
        long flightId = lookedUpFlightId();

        add(UUID.randomUUID(), "{\"flightId\":" + flightId + "}", HttpStatus.FORBIDDEN);
        add(user, "{\"flightId\":999999}", HttpStatus.NOT_FOUND);
        add(user, "{}", HttpStatus.BAD_REQUEST);
    }

    // ---- journeys and manual entry

    private String manualJfkToOtp(Long journeyId) throws Exception {
        long airline = airlineRepository.findByIcao("DLH").orElseThrow().getId();
        long jfk = airportRepository.findByIata("JFK").orElseThrow().getId();
        long otp = airportRepository.findByIata("OTP").orElseThrow().getId();
        return "{\"date\":\"2026-09-12\",\"airlineId\":" + airline + ",\"departureAirportId\":" + jfk + ",\"arrivalAirportId\":" + otp
                + (journeyId == null ? "" : ",\"journeyId\":" + journeyId) + ",\"cargo\":false}";
    }

    @Test
    void journeysOrderFlightsSuggestStopTypesAndSetVisibilityForAll() throws Exception {
        long flightId = lookedUpFlightId();
        long journeyId = add(user, "{\"flightId\":" + flightId + "}", HttpStatus.CREATED).number("$.journeyId");
        String manual = mockMvc.perform(json(as(user, post("/tailwind/flights/manual")), manualJfkToOtp(journeyId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.flight.source").value("MANUAL")).andReturn().getResponse().getContentAsString();
        long manualEntryId = new JsonPathReader(manual).number("$.id");

        mockMvc.perform(as(user, get("/tailwind/journeys/" + journeyId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("FRA - OTP"))
                .andExpect(jsonPath("$.flights.length()").value(2))
                .andExpect(jsonPath("$.flights[0].flight.flightNumber").value("LH400"))
                .andExpect(jsonPath("$.flights[0].stopTypeSuggested").value("LAYOVER"))
                .andExpect(jsonPath("$.flights[0].stopType").value("LAYOVER"))
                .andExpect(jsonPath("$.flights[1].stopType").doesNotExist());

        mockMvc.perform(json(as(user, patch("/tailwind/flights/" + manualEntryId)), "{\"seat\":\"3C\",\"seatPosition\":\"AISLE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seat").value("3C"));
        long firstEntryId = new JsonPathReader(mockMvc.perform(as(user, get("/tailwind/journeys/" + journeyId))).andReturn().getResponse().getContentAsString())
                .number("$.flights[0].id");
        mockMvc.perform(json(as(user, patch("/tailwind/flights/" + firstEntryId)), "{\"stopType\":\"LAYOVER_VISITED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stopType").value("LAYOVER_VISITED")).andExpect(jsonPath("$.stopTypeSuggested").value("LAYOVER"));

        mockMvc.perform(json(as(user, post("/tailwind/journeys/" + journeyId + "/visibility")), "{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flights[0].visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.flights[1].visibility").value("PRIVATE"));
        mockMvc.perform(as(user, get("/tailwind/flights/mine"))).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void anotherUserCannotSeeChangeOrDeleteSomeoneElsesJourneyOrFlights() throws Exception {
        long flightId = lookedUpFlightId();
        JsonPathReader added = add(user, "{\"flightId\":" + flightId + "}", HttpStatus.CREATED);
        long entryId = added.number("$.id");
        long journeyId = added.number("$.journeyId");
        UUID intruder = optedIn();

        mockMvc.perform(as(intruder, get("/tailwind/journeys/" + journeyId))).andExpect(status().isNotFound());
        mockMvc.perform(json(as(intruder, patch("/tailwind/flights/" + entryId)), "{\"visibility\":\"PRIVATE\"}")).andExpect(status().isNotFound());
        mockMvc.perform(as(intruder, delete("/tailwind/flights/" + entryId))).andExpect(status().isNotFound());
        mockMvc.perform(as(intruder, delete("/tailwind/journeys/" + journeyId))).andExpect(status().isNotFound());
        mockMvc.perform(json(as(intruder, post("/tailwind/journeys/" + journeyId + "/visibility")), "{\"visibility\":\"PRIVATE\"}")).andExpect(status().isNotFound());
        mockMvc.perform(as(intruder, get("/tailwind/journeys"))).andExpect(jsonPath("$.length()").value(0));
        // and cannot put their own flight into it
        long theirEntry = add(intruder, "{\"flightId\":" + flightId + "}", HttpStatus.CREATED).number("$.id");
        mockMvc.perform(json(as(intruder, patch("/tailwind/flights/" + theirEntry)), "{\"journeyId\":" + journeyId + "}")).andExpect(status().isNotFound());
    }

    @Test
    void movingAFlightAwayDropsTheEmptyAutomaticJourneyButKeepsATitledOne() throws Exception {
        long flightId = lookedUpFlightId();
        JsonPathReader added = add(user, "{\"flightId\":" + flightId + "}", HttpStatus.CREATED);
        long entryId = added.number("$.id");
        long automatic = added.number("$.journeyId");
        long titled = new JsonPathReader(mockMvc.perform(json(as(user, post("/tailwind/journeys")), "{\"title\":\"Summer\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Summer")).andExpect(jsonPath("$.titleIsCustom").value(true))
                .andReturn().getResponse().getContentAsString()).number("$.id");

        mockMvc.perform(json(as(user, patch("/tailwind/flights/" + entryId)), "{\"journeyId\":" + titled + "}")).andExpect(status().isOk());

        mockMvc.perform(as(user, get("/tailwind/journeys/" + automatic))).andExpect(status().isNotFound());
        mockMvc.perform(as(user, get("/tailwind/journeys/" + titled))).andExpect(status().isOk()).andExpect(jsonPath("$.flights.length()").value(1));

        // renaming back to blank returns to the automatic title
        mockMvc.perform(json(as(user, patch("/tailwind/journeys/" + titled)), "{\"title\":\" \"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("FRA - JFK")).andExpect(jsonPath("$.titleIsCustom").value(false));
    }

    @Test
    void deletingAManualEntryRemovesItsFlightRowAndDeletingAJourneyRemovesItsEntries() throws Exception {
        long flightId = lookedUpFlightId();
        long journeyId = add(user, "{\"flightId\":" + flightId + "}", HttpStatus.CREATED).number("$.journeyId");
        long manualEntry = new JsonPathReader(mockMvc.perform(json(as(user, post("/tailwind/flights/manual")), manualJfkToOtp(journeyId)))
                .andReturn().getResponse().getContentAsString()).number("$.id");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flights WHERE source = 'MANUAL'", Integer.class)).isEqualTo(1);

        mockMvc.perform(as(user, delete("/tailwind/flights/" + manualEntry))).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flights WHERE source = 'MANUAL'", Integer.class)).isZero();
        mockMvc.perform(as(user, get("/tailwind/journeys/" + journeyId))).andExpect(jsonPath("$.flights.length()").value(1));

        mockMvc.perform(as(user, delete("/tailwind/journeys/" + journeyId))).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_flights", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journeys", Integer.class)).isZero();
        // the shared API flight stays for everyone else
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flights WHERE source = 'API'", Integer.class)).isEqualTo(1);
    }

    @Test
    void manualEntryValidatesItsReferences() throws Exception {
        long airline = airlineRepository.findByIcao("DLH").orElseThrow().getId();
        long fra = airportRepository.findByIata("FRA").orElseThrow().getId();
        long jfk = airportRepository.findByIata("JFK").orElseThrow().getId();
        String base = "\"date\":\"2026-09-12\",\"airlineId\":" + airline;

        mockMvc.perform(json(as(user, post("/tailwind/flights/manual")), "{" + base + ",\"departureAirportId\":" + fra + ",\"arrivalAirportId\":" + fra + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(as(user, post("/tailwind/flights/manual")), "{" + base + ",\"departureAirportId\":" + fra + ",\"arrivalAirportId\":999999}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(as(user, post("/tailwind/flights/manual")), "{\"date\":\"2030-01-01\",\"airlineId\":" + airline + ",\"departureAirportId\":" + fra
                + ",\"arrivalAirportId\":" + jfk + "}")).andExpect(status().isBadRequest());
        mockMvc.perform(json(as(user, post("/tailwind/flights/manual")), "{" + base + ",\"departureAirportId\":" + fra + ",\"arrivalAirportId\":" + jfk
                + ",\"flightNumber\":\"lh 4\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(json(as(UUID.randomUUID(), post("/tailwind/flights/manual")), "{" + base + ",\"departureAirportId\":" + fra + ",\"arrivalAirportId\":" + jfk + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void manualEntryCanCarryAnAircraftTypeAndTheCargoFlag() throws Exception {
        long airline = airlineRepository.findByIcao("DLH").orElseThrow().getId();
        long fra = airportRepository.findByIata("FRA").orElseThrow().getId();
        long jfk = airportRepository.findByIata("JFK").orElseThrow().getId();
        long a346 = aircraftTypeRepository.findByIcaoCode("A346").orElseThrow().getId();

        mockMvc.perform(json(as(user, post("/tailwind/flights/manual")), "{\"date\":\"2026-09-12\",\"airlineId\":" + airline + ",\"departureAirportId\":" + fra
                        + ",\"arrivalAirportId\":" + jfk + ",\"aircraftTypeId\":" + a346 + ",\"cargo\":true,\"flightNumber\":\"LH8400\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flight.cargo").value(true))
                .andExpect(jsonPath("$.flight.aircraft.typeIcao").value("A346"))
                .andExpect(jsonPath("$.flight.aircraft.family").value("A340"))
                .andExpect(jsonPath("$.flight.distanceKm").value(org.hamcrest.Matchers.closeTo(6189, 15)));
    }

    // ---- aircraft resolution and cleanup

    @Test
    void aliasesResolveVariantsAndNeverGuessWhenAKeyIsAmbiguous() {
        assertThat(aliasRepository.count()).isGreaterThan(500);
        assertThat(aliasRepository.findById("7879")).hasValueSatisfying(a ->
                assertThat(a.getAircraftTypeId()).isEqualTo(aircraftTypeRepository.findByIcaoCode("B789").orElseThrow().getId()));
        assertThat(aliasRepository.existsById("737")).isFalse();

        assertThat(aircraftResolver.resolve("Boeing 787-9", null)).satisfies(r -> {
            assertThat(r.type().getIcaoCode()).isEqualTo("B789");
            assertThat(r.source()).isEqualTo(AircraftResolver.Source.ALIAS);
        });
        assertThat(aircraftResolver.resolve("Airbus A340", null)).satisfies(r -> {
            assertThat(r.type()).isNull();
            assertThat(r.family()).isEqualTo("A340");
            assertThat(r.source()).isEqualTo(AircraftResolver.Source.FAMILY);
        });
        assertThat(aircraftResolver.resolve("Airbus A340", "D-AIHX")).satisfies(r -> {
            assertThat(r.type().getIcaoCode()).isEqualTo("A346");
            assertThat(r.source()).isEqualTo(AircraftResolver.Source.REGISTRY);
        });
        assertThat(aircraftResolver.resolve("Airbus A340", "ZZ-NOPE").source()).isEqualTo(AircraftResolver.Source.FAMILY);
        assertThat(aircraftResolver.resolve(null, "D-AIHX").type().getIcaoCode()).isEqualTo("A346");
        // an exact model in the string wins over a registry entry for a different variant
        assertThat(aircraftResolver.resolve("Airbus A320", "D-AIHX").type().getIcaoCode()).isEqualTo("A320");
        assertThat(aircraftResolver.resolve("Totally Unknown Plane", null).source()).isEqualTo(AircraftResolver.Source.NONE);
        assertThat(aircraftResolver.resolve("", null).source()).isEqualTo(AircraftResolver.Source.NONE);
    }

    @Test
    void cleaningAPurgedUserRemovesTheirDataAndTheirManualFlightsButNotSharedFlights() throws Exception {
        long flightId = lookedUpFlightId();
        long journeyId = add(user, "{\"flightId\":" + flightId + "}", HttpStatus.CREATED).number("$.journeyId");
        mockMvc.perform(json(as(user, post("/tailwind/flights/manual")), manualJfkToOtp(journeyId))).andExpect(status().isCreated());
        UUID bystander = optedIn();
        add(bystander, "{\"flightId\":" + flightId + "}", HttpStatus.CREATED);

        userDataCleaner.deleteAllFor(List.of(user));

        assertThat(tailwindUserRepository.existsById(user)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journeys WHERE user_id = ?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_flights WHERE user_id = ?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flights WHERE source = 'MANUAL'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flights WHERE source = 'API'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_flights WHERE user_id = ?", Integer.class, bystander)).isEqualTo(1);
    }
}
