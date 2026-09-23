package org.kansei.tailwind.service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.flightdata.FlightDataClient;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.AircraftModelAliasRepository;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.stats.StatsFlightLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin tools over HTTP against a real Postgres: disabling a user, mapping aircraft strings and the CSV
 * import, plus canceled flights staying out of stats. The provider, Redis guards, RabbitMQ and shieldwall are replaced.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Import(AdminToolsIntegrationTest.FixedClock.class)
class AdminToolsIntegrationTest {

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
        registry.add("AERODATABOX_MONTHLY_UNIT_CAP", () -> "400");
        registry.add("TAILWIND_ADMIN_EMAIL", () -> "admin@example.test");
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
    private AircraftTypeRepository aircraftTypeRepository;
    @Autowired
    private AircraftModelAliasRepository aliasRepository;
    @Autowired
    private StatsFlightLoader statsFlightLoader;

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

    private UUID admin;
    private UUID user;

    @BeforeEach
    void freshUsers() {
        jdbc.execute("TRUNCATE csv_imports, user_flights, journeys, flights, friendships, tailwind_users RESTART IDENTITY CASCADE");
        admin = optedIn();
        user = optedIn();
        when(shieldwallUserClient.resolveUsernames(anyCollection())).thenReturn(Map.of(admin, "boss", user, "alexm"));
    }

    private UUID optedIn() {
        UUID id = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(id).joinedAt(Instant.now()).build());
        return id;
    }

    private static MockHttpServletRequestBuilder asAdmin(UUID who, MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", who.toString()).header("X-User-Role", "ADMIN");
    }

    private static MockHttpServletRequestBuilder as(UUID who, MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", who.toString());
    }

    // A manual flight straight in the tables, journey included
    private long manualFlight(UUID owner, String date, String aircraftRaw, String status, String visibility) {
        long airline = airlineRepository.findByIcao("DLH").orElseThrow().getId();
        long fra = airportRepository.findByIata("FRA").orElseThrow().getId();
        long otp = airportRepository.findByIata("OTP").orElseThrow().getId();
        Long flightId = jdbc.queryForObject("INSERT INTO flights (source, flight_number, flight_date, airline_id, departure_airport_id,"
                + " arrival_airport_id, status, aircraft_model_raw, distance_km, created_at) VALUES ('MANUAL', 'LH1650', ?::date, ?, ?, ?, ?, ?, 1400, now())"
                + " RETURNING id", Long.class, date, airline, fra, otp, status, aircraftRaw);
        Long journeyId = jdbc.queryForObject("INSERT INTO journeys (user_id, created_at) VALUES (?, now()) RETURNING id", Long.class, owner);
        return jdbc.queryForObject("INSERT INTO user_flights (user_id, journey_id, flight_id, visibility, created_at) VALUES (?, ?, ?, ?, now())"
                + " RETURNING id", Long.class, owner, journeyId, flightId, visibility);
    }

    // ---- canceled flights

    @Test
    void aCanceledFlightStaysInTheLogButOutOfStats() throws Exception {
        manualFlight(user, "2026-09-01", null, "Canceled", "PUBLIC");
        manualFlight(user, "2026-09-02", null, "Arrived", "PUBLIC");

        assertThat(statsFlightLoader.load(user, user, null)).hasSize(1);
        mockMvc.perform(as(user, get("/tailwind/flights/mine")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.flight.status == 'Canceled')].flight.canceled").value(true));
    }

    // ---- disable

    @Test
    void anAdminDisablesAUserWhoIsThenLockedOutAndHiddenFromEveryone() throws Exception {
        manualFlight(user, "2026-09-01", null, null, "PUBLIC");
        UUID stranger = optedIn();
        when(shieldwallUserClient.searchUsers(eq("alex"), anyInt())).thenReturn(List.of(new ShieldwallUserClient.UserMatch(user, "alexm")));

        mockMvc.perform(asAdmin(admin, get("/tailwind/admin/users/search").param("query", "alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alexm"))
                .andExpect(jsonPath("$[0].enabled").value(true));
        mockMvc.perform(as(stranger, get("/tailwind/flights/users/" + user))).andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(asAdmin(admin, post("/tailwind/admin/users/" + user + "/disable"))).andExpect(status().isNoContent());

        mockMvc.perform(as(user, get("/tailwind/users/me"))).andExpect(status().isForbidden());
        mockMvc.perform(as(stranger, get("/tailwind/flights/users/" + user))).andExpect(jsonPath("$.length()").value(0));
        assertThat(statsFlightLoader.load(stranger, user, null)).isEmpty();
        mockMvc.perform(as(stranger, get("/tailwind/friends/search").param("q", "alex"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(asAdmin(admin, get("/tailwind/admin/users/search").param("query", "alex")))
                .andExpect(jsonPath("$[0].enabled").value(false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_flights WHERE user_id = ?", Integer.class, user)).isEqualTo(1);
        verify(auditPublisher).publishWithResolvedUsername(eq("DISABLE_USER"), eq(admin), eq("TAILWIND_USER"), eq(user.toString()), any());
    }

    @Test
    void aDisabledUserIsListedAndCanBeReinstatedWithTheirLogIntact() throws Exception {
        manualFlight(user, "2026-09-01", null, null, "PUBLIC");
        UUID stranger = optedIn();
        mockMvc.perform(asAdmin(admin, post("/tailwind/admin/users/" + user + "/disable"))).andExpect(status().isNoContent());

        mockMvc.perform(asAdmin(admin, get("/tailwind/admin/users/disabled")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("alexm"))
                .andExpect(jsonPath("$[0].disabledAt").value("2026-09-20T12:00:00Z"));

        mockMvc.perform(asAdmin(admin, post("/tailwind/admin/users/" + user + "/enable"))).andExpect(status().isNoContent());

        mockMvc.perform(asAdmin(admin, get("/tailwind/admin/users/disabled"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(user, get("/tailwind/flights/mine"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(stranger, get("/tailwind/flights/users/" + user))).andExpect(jsonPath("$.length()").value(1));
        verify(auditPublisher).publishWithResolvedUsername(eq("ENABLE_USER"), eq(admin), eq("TAILWIND_USER"), eq(user.toString()), any());
        mockMvc.perform(as(user, get("/tailwind/admin/users/disabled"))).andExpect(status().isForbidden());
    }

    @Test
    void disablingNeedsAnAdminAnotherUserAndAnExistingOne() throws Exception {
        mockMvc.perform(as(admin, post("/tailwind/admin/users/" + user + "/disable"))).andExpect(status().isForbidden());
        mockMvc.perform(asAdmin(admin, post("/tailwind/admin/users/" + admin + "/disable"))).andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(admin, post("/tailwind/admin/users/" + UUID.randomUUID() + "/disable"))).andExpect(status().isNotFound());
        assertThat(tailwindUserRepository.findById(user).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void aUserCanAskForTheirAccountToBeDisabled() throws Exception {
        mockMvc.perform(as(user, post("/tailwind/users/me/disable-request"))).andExpect(status().isNoContent());
        verify(mailEventPublisher).publishDisableRequest(user, "alexm");
    }

    // ---- unmapped aircraft strings

    @Test
    void mappingAnAircraftStringFixesStoredFlightsAndLaterResolutions() throws Exception {
        manualFlight(user, "2026-09-01", "Skyliner Mystery 9", null, "PUBLIC");
        manualFlight(user, "2026-09-02", "Skyliner Mystery 9", null, "PUBLIC");
        long a320 = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow().getId();

        mockMvc.perform(asAdmin(admin, get("/tailwind/admin/aircraft-strings/unmapped")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modelString").value("Skyliner Mystery 9"))
                .andExpect(jsonPath("$[0].flightCount").value(2));

        mockMvc.perform(asAdmin(admin, post("/tailwind/admin/aircraft-strings/mappings"))
                        .contentType("application/json")
                        .content("{\"modelString\":\"Skyliner Mystery 9\",\"aircraftTypeId\":" + a320 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightsUpdated").value(2))
                .andExpect(jsonPath("$.aircraftType.icaoCode").value("A320"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flights WHERE aircraft_type_id = ?", Integer.class, a320)).isEqualTo(2);
        mockMvc.perform(asAdmin(admin, get("/tailwind/admin/aircraft-strings/unmapped"))).andExpect(jsonPath("$.length()").value(0));
        assertThat(aliasRepository.findAll()).anySatisfy(alias -> assertThat(alias.getAircraftTypeId()).isEqualTo(a320));
        verify(auditPublisher).publishWithResolvedUsername(eq("MAP_AIRCRAFT_STRING"), eq(admin), eq("AIRCRAFT_TYPE"), eq(String.valueOf(a320)), any());
    }

    @Test
    void mappingIsAdminOnly() throws Exception {
        mockMvc.perform(as(user, get("/tailwind/admin/aircraft-strings/unmapped"))).andExpect(status().isForbidden());
    }

    // ---- CSV import

    private static final String CSV = """
            date,flight_number,airline,from,to,departure_time,arrival_time,aircraft,seat,cabin_class,journey,cargo,visibility
            2024-05-01,RO302,,FRA,OTP,10:00,13:30,A320,12A,economy,Spring trip,,public
            2024-05-02,LH1650,,FRA,OTP,,,,,,,
            2024-05-08,,DLH,OTP,FRA,,,Skyliner Mystery 9,,,Spring trip,no
            2024-05-08,,DLH,OTP,FRA,,,,,,,
            2024-06-01,LH1650,DLH,FRA,XXX,,,,,,,
            2031-01-01,LH1650,,FRA,OTP,,,,,,,
            """;

    private static MockMultipartFile file(String text) {
        return new MockMultipartFile("file", "backfill.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions upload(String path, UUID target, String text) throws Exception {
        return mockMvc.perform(multipart(path).file(file(text)).param("targetUserId", target.toString())
                .header("X-User-Id", admin.toString()).header("X-User-Role", "ADMIN"));
    }

    @Test
    void thePreviewShowsEveryRowAndWritesNothing() throws Exception {
        upload("/tailwind/admin/imports/preview", user, CSV)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUsername").value("alexm"))
                .andExpect(jsonPath("$.totalRows").value(6))
                .andExpect(jsonPath("$.readyRows").value(3))
                .andExpect(jsonPath("$.duplicateRows").value(1))
                .andExpect(jsonPath("$.errorRows").value(2))
                .andExpect(jsonPath("$.rows[0].line").value(2))
                .andExpect(jsonPath("$.rows[0].airline").value("Tarom"))
                .andExpect(jsonPath("$.rows[1].airline").value("Lufthansa"))
                .andExpect(jsonPath("$.rows[2].aircraft").value("Skyliner Mystery 9 (unmapped)"))
                .andExpect(jsonPath("$.rows[3].status").value("DUPLICATE"))
                .andExpect(jsonPath("$.rows[4].message").value("to airport XXX is unknown"))
                .andExpect(jsonPath("$.rows[5].status").value("ERROR"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flights", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM csv_imports", Integer.class)).isZero();
    }

    @Test
    void committingWritesPrivateFlightsGroupsJourneysAndRecordsTheRun() throws Exception {
        manualFlight(user, "2024-06-10", null, null, "PUBLIC");

        upload("/tailwind/admin/imports", user, CSV)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedRows").value(3))
                .andExpect(jsonPath("$.duplicateRows").value(1))
                .andExpect(jsonPath("$.errorRows").value(2))
                .andExpect(jsonPath("$.errors[0].line").value(6))
                .andExpect(jsonPath("$.targetUsername").value("alexm"));

        // The user's default (FRIENDS) unless the row names one, the row with "public" is the only other imported one
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_flights WHERE user_id = ? AND visibility = 'FRIENDS'", Integer.class, user))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_flights WHERE user_id = ? AND visibility = 'PUBLIC'", Integer.class, user))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journeys WHERE user_id = ? AND title = 'Spring trip'", Integer.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT journey_id) FROM user_flights uf JOIN journeys j ON j.id = uf.journey_id"
                + " WHERE j.title = 'Spring trip'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT f.arrival_scheduled_utc IS NOT NULL AS timed, a.icao AS airline FROM flights f JOIN airlines a"
                + " ON a.id = f.airline_id WHERE f.flight_date = '2024-05-01'"))
                .containsEntry("timed", true).containsEntry("airline", "ROT");
        verify(auditPublisher).publishWithResolvedUsername(eq("IMPORT_FLIGHTS"), eq(admin), eq("TAILWIND_USER"), eq(user.toString()), any());

        mockMvc.perform(asAdmin(admin, get("/tailwind/admin/imports")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("backfill.csv"))
                .andExpect(jsonPath("$[0].adminUsername").value("boss"));

        // The same file again finds everything already there
        upload("/tailwind/admin/imports/preview", user, CSV).andExpect(jsonPath("$.readyRows").value(0));
    }

    @Test
    void theImportRefusesBadFilesStrangersAndNonAdmins() throws Exception {
        upload("/tailwind/admin/imports/preview", user, "date,from,to,colour\n2024-05-01,FRA,OTP,red\n").andExpect(status().isBadRequest());
        upload("/tailwind/admin/imports/preview", user, "date,from\n2024-05-01,FRA\n").andExpect(status().isBadRequest());
        upload("/tailwind/admin/imports/preview", user, "date,from,to\n").andExpect(status().isBadRequest());
        upload("/tailwind/admin/imports/preview", UUID.randomUUID(), CSV).andExpect(status().isNotFound());
        mockMvc.perform(multipart("/tailwind/admin/imports").file(file(CSV)).param("targetUserId", user.toString()).header("X-User-Id", admin.toString()))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flights", Integer.class)).isZero();
        verify(auditPublisher, never()).publishWithResolvedUsername(eq("IMPORT_FLIGHTS"), any(), anyString(), anyString(), any());
    }
}
