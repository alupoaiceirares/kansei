package org.kansei.tailwind.service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.flightdata.FlightDataClient;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
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
 * Flights taken together (shared between friends, "I was on this too" with approval) and the yearly recap with
 * its January email, against a real Postgres. Provider, Redis guards, RabbitMQ and shieldwall are replaced.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Import(SharedFlightsAndRecapIntegrationTest.FixedClock.class)
class SharedFlightsAndRecapIntegrationTest {

    // Early January, so the recap job works on the year before
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-01-05T09:00:00Z"), ZoneOffset.UTC);
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
        registry.add("AERODATABOX_MONTHLY_UNIT_CAP", () -> "400");
        registry.add("TAILWIND_ADMIN_EMAIL", () -> "admin@example.test");
        registry.add("BLACKBIRD_URL", () -> "http://localhost:5173/");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TailwindUserRepository tailwindUserRepository;
    @Autowired
    private AirportRepository airportRepository;
    @Autowired
    private AirlineRepository airlineRepository;
    @Autowired
    private YearlyRecapMailer yearlyRecapMailer;

    @MockitoBean
    private FlightDataClient flightDataClient;
    @MockitoBean
    private LookupQuotaGuard quotaGuard;
    @MockitoBean
    private LookupRateLimiter rateLimiter;
    @MockitoBean
    private LookupMissCache missCache;
    @MockitoBean
    private RefreshCooldown refreshCooldown;
    @MockitoBean
    private AuditPublisher auditPublisher;
    @MockitoBean
    private MailEventPublisher mailEventPublisher;
    @MockitoBean
    private ShieldwallUserClient shieldwallUserClient;

    private UUID alex;
    private UUID bea;
    private UUID stranger;

    @BeforeEach
    void freshUsers() throws Exception {
        jdbc.execute("TRUNCATE yearly_recaps, flight_join_requests, user_flights, journeys, flights, friendships, tailwind_users"
                + " RESTART IDENTITY CASCADE");
        alex = optedIn();
        bea = optedIn();
        stranger = optedIn();
        when(shieldwallUserClient.resolveUsernames(anyCollection())).thenReturn(Map.of(alex, "alex", bea, "bea", stranger, "sam"));
        mockMvc.perform(as(alex, post("/tailwind/friends/requests/" + bea))).andExpect(status().is2xxSuccessful());
        mockMvc.perform(as(bea, post("/tailwind/friends/requests/" + alex + "/accept"))).andExpect(status().is2xxSuccessful());
    }

    private UUID optedIn() {
        UUID id = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(id).joinedAt(Instant.now()).build());
        return id;
    }

    private static MockHttpServletRequestBuilder as(UUID who, MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", who.toString());
    }

    private long flight(String source, String date, String from, String to, String number, String family) {
        long airline = airlineRepository.findByIcao("ROT").orElseThrow().getId();
        long dep = airportRepository.findByIata(from).orElseThrow().getId();
        long arr = airportRepository.findByIata(to).orElseThrow().getId();
        return jdbc.queryForObject("INSERT INTO flights (source, flight_number, flight_date, airline_id, departure_airport_id, arrival_airport_id,"
                + " aircraft_family, distance_km, created_at) VALUES (?, ?, ?::date, ?, ?, ?, ?, 1000, now()) RETURNING id", Long.class,
                source, number, date, airline, dep, arr, family);
    }

    private long entry(UUID owner, long flightId, String visibility) {
        Long journeyId = jdbc.queryForObject("INSERT INTO journeys (user_id, created_at) VALUES (?, now()) RETURNING id", Long.class, owner);
        return jdbc.queryForObject("INSERT INTO user_flights (user_id, journey_id, flight_id, visibility, created_at) VALUES (?, ?, ?, ?, now())"
                + " RETURNING id", Long.class, owner, journeyId, flightId, visibility);
    }

    private int entriesOf(UUID user, long flightId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_flights WHERE user_id = ? AND flight_id = ?", Integer.class, user, flightId);
    }

    // ---- shared flights

    @Test
    void friendsOnTheSameFlightSeeEachOtherButStrangersAndPrivateEntriesStayHidden() throws Exception {
        long shared = flight("API", "2025-06-01", "OTP", "FRA", "RO301", null);
        long mine = entry(alex, shared, "FRIENDS");
        entry(bea, shared, "FRIENDS");
        entry(stranger, shared, "PUBLIC");

        mockMvc.perform(as(alex, get("/tailwind/flights/" + mine + "/shared")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("bea"));

        jdbc.update("UPDATE user_flights SET visibility = 'PRIVATE' WHERE user_id = ?", bea);
        mockMvc.perform(as(alex, get("/tailwind/flights/" + mine + "/shared"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(bea, get("/tailwind/flights/" + mine + "/shared"))).andExpect(status().isNotFound());
    }

    // ---- I was on this too

    @Test
    void aFriendsLookedUpFlightIsAddedStraightAway() throws Exception {
        long api = flight("API", "2025-06-01", "OTP", "FRA", "RO301", null);
        long beas = entry(bea, api, "FRIENDS");

        mockMvc.perform(as(alex, post("/tailwind/flights/" + beas + "/join")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ADDED"))
                .andExpect(jsonPath("$.userFlight.flight.id").value(api))
                .andExpect(jsonPath("$.userFlight.visibility").value("FRIENDS"));

        assertThat(entriesOf(alex, api)).isEqualTo(1);
        mockMvc.perform(as(alex, post("/tailwind/flights/" + beas + "/join"))).andExpect(status().isConflict());
    }

    @Test
    void aStrangerOrAFriendsManualFlightNeedsTheOwnersApproval() throws Exception {
        long api = flight("API", "2025-06-01", "OTP", "FRA", "RO301", null);
        long publicEntry = entry(bea, api, "PUBLIC");
        long manual = flight("MANUAL", "2025-07-01", "FRA", "OTP", null, null);
        long manualEntry = entry(bea, manual, "FRIENDS");

        mockMvc.perform(as(stranger, post("/tailwind/flights/" + publicEntry + "/join"))).andExpect(jsonPath("$.outcome").value("REQUESTED"));
        mockMvc.perform(as(alex, post("/tailwind/flights/" + manualEntry + "/join"))).andExpect(jsonPath("$.outcome").value("REQUESTED"));
        mockMvc.perform(as(alex, post("/tailwind/flights/" + manualEntry + "/join"))).andExpect(status().isConflict());
        assertThat(entriesOf(stranger, api)).isZero();
        assertThat(entriesOf(alex, manual)).isZero();

        String requests = mockMvc.perform(as(bea, get("/tailwind/flights/join-requests")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.otherUsername == 'alex')].direction").value("INCOMING"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(as(alex, get("/tailwind/flights/join-requests")))
                .andExpect(jsonPath("$[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$[0].otherUsername").value("bea"));

        long alexRequest = ((Number) JsonPath.<List<Object>>read(requests, "$[?(@.otherUsername == 'alex')].id").get(0)).longValue();
        long strangerRequest = ((Number) JsonPath.<List<Object>>read(requests, "$[?(@.otherUsername == 'sam')].id").get(0)).longValue();

        // Only the owner answers
        mockMvc.perform(as(alex, post("/tailwind/flights/join-requests/" + alexRequest + "/accept"))).andExpect(status().isNotFound());
        mockMvc.perform(as(bea, post("/tailwind/flights/join-requests/" + alexRequest + "/accept"))).andExpect(status().isNoContent());
        mockMvc.perform(as(bea, post("/tailwind/flights/join-requests/" + strangerRequest + "/decline"))).andExpect(status().isNoContent());

        assertThat(entriesOf(alex, manual)).isEqualTo(1);
        assertThat(entriesOf(stranger, api)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flight_join_requests", Integer.class)).isZero();

        // The manual flight row is shared now, so it survives its creator deleting their entry
        mockMvc.perform(as(bea, delete("/tailwind/flights/" + manualEntry))).andExpect(status().isNoContent());
        assertThat(entriesOf(alex, manual)).isEqualTo(1);
        mockMvc.perform(as(alex, get("/tailwind/flights/mine"))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void hiddenAndOwnFlightsCannotBeJoinedAndARequesterCanWithdraw() throws Exception {
        long api = flight("API", "2025-06-01", "OTP", "FRA", "RO301", null);
        long privateEntry = entry(bea, api, "PRIVATE");
        long friendsEntry = entry(alex, flight("API", "2025-06-02", "FRA", "OTP", "RO302", null), "FRIENDS");

        mockMvc.perform(as(alex, post("/tailwind/flights/" + privateEntry + "/join"))).andExpect(status().isNotFound());
        mockMvc.perform(as(stranger, post("/tailwind/flights/" + friendsEntry + "/join"))).andExpect(status().isNotFound());
        mockMvc.perform(as(alex, post("/tailwind/flights/" + friendsEntry + "/join"))).andExpect(status().isNotFound());

        jdbc.update("UPDATE user_flights SET visibility = 'PUBLIC' WHERE id = ?", friendsEntry);
        mockMvc.perform(as(stranger, post("/tailwind/flights/" + friendsEntry + "/join"))).andExpect(jsonPath("$.outcome").value("REQUESTED"));
        Long requestId = jdbc.queryForObject("SELECT id FROM flight_join_requests", Long.class);
        mockMvc.perform(as(alex, delete("/tailwind/flights/join-requests/" + requestId))).andExpect(status().isNotFound());
        mockMvc.perform(as(stranger, delete("/tailwind/flights/join-requests/" + requestId))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flight_join_requests", Integer.class)).isZero();
    }

    // ---- yearly recap

    @Test
    void theRecapCountsOnlyThatYearAndMarksWhatWasNew() throws Exception {
        entry(alex, flight("MANUAL", "2024-03-01", "OTP", "FRA", null, "A320 family"), "PRIVATE");
        entry(alex, flight("MANUAL", "2025-02-10", "OTP", "FRA", null, "A320 family"), "PRIVATE");
        entry(alex, flight("MANUAL", "2025-02-20", "FRA", "JFK", null, "777"), "PRIVATE");
        long canceled = flight("MANUAL", "2025-05-01", "OTP", "LHR", null, null);
        jdbc.update("UPDATE flights SET status = 'Canceled' WHERE id = ?", canceled);
        entry(alex, canceled, "PRIVATE");

        mockMvc.perform(as(alex, get("/tailwind/recaps"))).andExpect(jsonPath("$[0]").value(2025)).andExpect(jsonPath("$[1]").value(2024));
        mockMvc.perform(as(alex, get("/tailwind/recaps/2025")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightCount").value(2))
                .andExpect(jsonPath("$.distanceKm").value(2000.0))
                .andExpect(jsonPath("$.monthCounts[1]").value(2))
                .andExpect(jsonPath("$.busiestMonth").value(2))
                .andExpect(jsonPath("$.newCountries[?(@.code == 'US')]").exists())
                .andExpect(jsonPath("$.newCountries[?(@.code == 'DE')]").doesNotExist())
                .andExpect(jsonPath("$.newAircraftFamilies[0]").value("777"))
                .andExpect(jsonPath("$.newAircraftFamilies.length()").value(1))
                .andExpect(jsonPath("$.topAirline.name").value("Tarom"));
    }

    @Test
    void theJanuaryJobMailsLastYearOnceAndSkipsOptedOutAndUnreachableUsers() throws Exception {
        entry(alex, flight("MANUAL", "2025-02-10", "OTP", "FRA", null, null), "PRIVATE");
        entry(bea, flight("MANUAL", "2025-03-10", "OTP", "FRA", null, null), "PRIVATE");
        entry(stranger, flight("MANUAL", "2025-04-10", "OTP", "FRA", null, null), "PRIVATE");
        mockMvc.perform(as(bea, patch("/tailwind/users/me")).contentType("application/json").content("{\"recapEmails\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recapEmails").value(false));
        // The stranger has no verified address yet
        when(shieldwallUserClient.findContacts(anyCollection()))
                .thenReturn(List.of(new ShieldwallUserClient.Contact(alex, "alex", "alex@example.test")));

        assertThat(yearlyRecapMailer.sendLastYear()).isEqualTo(1);
        verify(mailEventPublisher).publishYearlyRecap(eq("alex@example.test"), any());
        verify(mailEventPublisher, times(1)).publishYearlyRecap(any(), anyMap());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM yearly_recaps WHERE recap_year = 2025 AND emailed_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM yearly_recaps WHERE user_id = ?", Integer.class, bea)).isZero();

        // Next day: alex is done, the stranger is still waiting for an address
        assertThat(yearlyRecapMailer.sendLastYear()).isZero();
        verify(mailEventPublisher, times(1)).publishYearlyRecap(any(), anyMap());
        verify(mailEventPublisher, never()).publishYearlyRecap(eq("bea@example.test"), any());
    }

    @Test
    void theMailCarriesTheRecapNumbersAndLinks() {
        var vars = yearlyRecapMailer.vars("alex", new org.kansei.tailwind.dto.YearlyRecapResponse(2025, 3, 40075, 1.0, 0.1, 600, 3, 2,
                List.of(new org.kansei.tailwind.dto.YearlyRecapResponse.NamedCode("US", "United States")), 4, 2, List.of(),
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), null, null, null, null));

        assertThat(vars).containsEntry("distance", "40,075 km").containsEntry("hours", "10").containsEntry("newCountries", "United States")
                .containsEntry("recapUrl", "http://localhost:5173/recap?year=2025");
    }
}
