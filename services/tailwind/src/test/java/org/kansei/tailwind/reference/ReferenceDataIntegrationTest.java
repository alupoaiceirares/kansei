package org.kansei.tailwind.reference;

import org.junit.jupiter.api.Test;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.CountryRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.service.AuditPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres (Testcontainers): the Liquibase changesets, Hibernate schema validation, the CSV
 * import on startup, the search queries and the admin endpoints end to end. RabbitMQ, Redis and
 * shieldwall are out of scope here, so the audit publisher and shieldwall client are mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ReferenceDataIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Every placeholder in application.properties needs a resolvable value, none of these are reached
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
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AirportRepository airportRepository;
    @Autowired
    private AirlineRepository airlineRepository;
    @Autowired
    private AircraftTypeRepository aircraftTypeRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private TailwindUserRepository tailwindUserRepository;
    @Autowired
    private ReferenceDataLoader referenceDataLoader;

    @MockitoBean
    private AuditPublisher auditPublisher;
    @MockitoBean
    private ShieldwallUserClient shieldwallUserClient;

    private static final String USER = UUID.randomUUID().toString();

    @Test
    void startupImportedTheBundledData() {
        assertThat(countryRepository.count()).isGreaterThan(200);
        assertThat(airportRepository.count()).isGreaterThan(8000);
        assertThat(airlineRepository.count()).isGreaterThan(1000);
        assertThat(aircraftTypeRepository.count()).isGreaterThan(600);
    }

    @Test
    void importIsSkippedForTablesThatAlreadyHaveRows() throws Exception {
        long airports = airportRepository.count();

        referenceDataLoader.run(new DefaultApplicationArguments());

        assertThat(airportRepository.count()).isEqualTo(airports);
    }

    @Test
    void airportSearchPutsAnExactCodeFirstAndFindsByCity() throws Exception {
        mockMvc.perform(get("/tailwind/airports/search").param("q", "otp").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].iata").value("OTP"))
                .andExpect(jsonPath("$[0].icao").value("LROP"))
                .andExpect(jsonPath("$[0].timeZone").value("Europe/Bucharest"));

        mockMvc.perform(get("/tailwind/airports/search").param("q", "frankfurt").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.iata=='FRA')]").exists());
    }

    @Test
    void airlineSearchFindsByIataCodeAndByName() throws Exception {
        mockMvc.perform(get("/tailwind/airlines/search").param("q", "wizz").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].icao").value("WZZ"));

        mockMvc.perform(get("/tailwind/airlines/search").param("q", "LH").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.icao=='DLH')]").exists());
    }

    @Test
    void aircraftTypeSearchFindsByDesignatorAndFamily() throws Exception {
        mockMvc.perform(get("/tailwind/aircraft-types/search").param("q", "a320").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].icaoCode").value("A320"))
                .andExpect(jsonPath("$[0].family").value("A320 family"));

        mockMvc.perform(get("/tailwind/aircraft-types/search").param("q", "737-800").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.icaoCode=='B738')]").exists());
    }

    @Test
    void likeWildcardsInTheQueryAreTreatedAsLiteralText() throws Exception {
        mockMvc.perform(get("/tailwind/airports/search").param("q", "%%").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void tooShortQueryIsABadRequest() throws Exception {
        mockMvc.perform(get("/tailwind/airports/search").param("q", "a").header("X-User-Id", USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanAddAndEditAnAirportAndIsAudited() throws Exception {
        UUID admin = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(admin).joinedAt(Instant.now()).build());
        String body = """
                {"icao":"LRXX","iata":"XXR","name":"Test Field","city":"Testville","countryCode":"RO",
                 "latitude":45.5,"longitude":25.5,"timeZone":"Europe/Bucharest"}
                """;

        String created = mockMvc.perform(post("/tailwind/admin/airports").header("X-User-Id", admin.toString()).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iata").value("XXR"))
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/tailwind/admin/airports").header("X-User-Id", admin.toString()).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/tailwind/admin/airports/" + id).header("X-User-Id", admin.toString()).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("Test Field", "Renamed Field")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Field"));

        verify(auditPublisher).publishWithResolvedUsername(eq("CREATE_AIRPORT"), eq(admin), eq("AIRPORT"), eq(id), any());
        verify(auditPublisher).publishWithResolvedUsername(eq("UPDATE_AIRPORT"), eq(admin), eq("AIRPORT"), eq(id), any());
    }

    @Test
    void nonAdminCannotEditReferenceData() throws Exception {
        UUID user = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(user).joinedAt(Instant.now()).build());

        mockMvc.perform(post("/tailwind/admin/countries").header("X-User-Id", user.toString()).header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ZZ\",\"name\":\"Nowhere\",\"continent\":\"EU\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidAirportRequestIsRejected() throws Exception {
        UUID admin = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(admin).joinedAt(Instant.now()).build());

        mockMvc.perform(post("/tailwind/admin/airports").header("X-User-Id", admin.toString()).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"icao\":\"bad\",\"name\":\"X\",\"countryCode\":\"RO\",\"latitude\":200,\"longitude\":0}"))
                .andExpect(status().isBadRequest());
    }
}
