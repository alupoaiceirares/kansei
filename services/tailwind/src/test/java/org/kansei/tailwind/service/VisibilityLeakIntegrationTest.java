package org.kansei.tailwind.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.model.Flight;
import org.kansei.tailwind.model.FlightSource;
import org.kansei.tailwind.model.Friendship;
import org.kansei.tailwind.model.FriendshipId;
import org.kansei.tailwind.model.FriendshipStatus;
import org.kansei.tailwind.model.Journey;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.model.Visibility;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.FlightRepository;
import org.kansei.tailwind.repository.FriendshipRepository;
import org.kansei.tailwind.repository.JourneyRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What each kind of viewer may see of someone else's flights, across the profile query, the journey list and
 * the flight list. The owner keeps a private, a friends-only and a public flight, each on its own aircraft and
 * route, so a leak shows up in the totals as well as in the lists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class VisibilityLeakIntegrationTest {

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
        registry.add("BLACKBIRD_URL", () -> "http://localhost:5173");
    }

    private static final String GRAPHQL = "/tailwind/graphql";
    private static final LocalDate DAY = LocalDate.now().minusMonths(6).withDayOfMonth(12);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TailwindUserRepository tailwindUserRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private JourneyRepository journeyRepository;
    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private UserFlightRepository userFlightRepository;
    @Autowired
    private AirportRepository airportRepository;
    @Autowired
    private AirlineRepository airlineRepository;
    @Autowired
    private AircraftTypeRepository aircraftTypeRepository;

    @MockitoBean
    private AuditPublisher auditPublisher;
    @MockitoBean
    private ShieldwallUserClient shieldwallUserClient;

    private UUID owner;
    private UUID friend;
    private UUID stranger;

    @BeforeEach
    void setUp() {
        userFlightRepository.deleteAll();
        journeyRepository.deleteAll();
        flightRepository.deleteAll();
        friendshipRepository.deleteAll();
        tailwindUserRepository.deleteAll();
        owner = optedIn();
        friend = optedIn();
        stranger = optedIn();
        friendshipRepository.save(Friendship.builder().id(FriendshipId.of(owner, friend)).requestedBy(owner)
                .status(FriendshipStatus.ACCEPTED).requestedAt(Instant.now()).respondedAt(Instant.now()).build());

        // One journey, three flights, one per visibility level, each with its own aircraft and destination
        long trip = journeyRepository.save(Journey.builder().userId(owner).createdAt(Instant.now()).build()).getId();
        log(trip, flight("LH1", DAY, "OTP", "FRA", "A320"), Visibility.PUBLIC);
        log(trip, flight("LH2", DAY.plusDays(2), "FRA", "JFK", "A346"), Visibility.FRIENDS);
        log(trip, flight("LH3", DAY.plusDays(5), "JFK", "PEK", "B744"), Visibility.PRIVATE);
    }

    private UUID optedIn() {
        UUID id = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(id).joinedAt(Instant.now()).build());
        return id;
    }

    private Flight flight(String number, LocalDate date, String fromIata, String toIata, String typeIcao) {
        var departure = airportRepository.findByIata(fromIata).orElseThrow();
        var arrival = airportRepository.findByIata(toIata).orElseThrow();
        var type = aircraftTypeRepository.findByIcaoCode(typeIcao).orElseThrow();
        return flightRepository.save(Flight.builder()
                .source(FlightSource.API).flightNumber(number).flightDate(date)
                .airline(airlineRepository.findByIcao("DLH").orElseThrow())
                .departureAirport(departure).arrivalAirport(arrival)
                .aircraftType(type).aircraftFamily(type.getFamily())
                .distanceKm(GeoDistance.haversineKm(departure.getLatitude(), departure.getLongitude(),
                        arrival.getLatitude(), arrival.getLongitude()))
                .createdAt(Instant.now()).build());
    }

    private void log(long journeyId, Flight flight, Visibility visibility) {
        userFlightRepository.save(UserFlight.builder().userId(owner).journeyId(journeyId).flight(flight)
                .visibility(visibility).createdAt(Instant.now()).build());
    }

    private ResultActions profileOfOwner(UUID viewer) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", "query P($u: ID) { travelProfile(userId: $u) { stats { flightCount aircraftTypeCount airportCount } "
                + "countries { visited { code } } aircraft { family } airports { iata } records { longestFlight { flightNumber } } } }");
        body.put("variables", Map.of("u", owner.toString()));
        return mockMvc.perform(post(GRAPHQL).header("X-User-Id", viewer.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void theOwnerSeesEverythingOfTheirOwn() throws Exception {
        profileOfOwner(owner)
                .andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(3))
                .andExpect(jsonPath("$.data.travelProfile.stats.aircraftTypeCount").value(3))
                .andExpect(jsonPath("$.data.travelProfile.records.longestFlight.flightNumber").value("LH3"));
    }

    @Test
    void aFriendSeesFriendsAndPublicButNeverPrivate() throws Exception {
        profileOfOwner(friend)
                .andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(2))
                .andExpect(jsonPath("$.data.travelProfile.stats.aircraftTypeCount").value(2))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[*].family",
                        org.hamcrest.Matchers.containsInAnyOrder("A320 family", "A340")))
                .andExpect(jsonPath("$.data.travelProfile.airports[*].iata", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("PEK"))))
                .andExpect(jsonPath("$.data.travelProfile.countries.visited[*].code", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CN"))))
                .andExpect(jsonPath("$.data.travelProfile.records.longestFlight.flightNumber").value("LH2"));
    }

    @Test
    void aStrangerSeesOnlyPublic() throws Exception {
        profileOfOwner(stranger)
                .andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(1))
                .andExpect(jsonPath("$.data.travelProfile.stats.aircraftTypeCount").value(1))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[*].family", org.hamcrest.Matchers.contains("A320 family")))
                .andExpect(jsonPath("$.data.travelProfile.airports[*].iata", org.hamcrest.Matchers.containsInAnyOrder("OTP", "FRA")))
                .andExpect(jsonPath("$.data.travelProfile.records.longestFlight.flightNumber").value("LH1"));
    }

    @Test
    void unfriendingImmediatelyTakesTheFriendsOnlyFlightsBack() throws Exception {
        profileOfOwner(friend).andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(2));

        friendshipRepository.deleteById(FriendshipId.of(owner, friend));

        profileOfOwner(friend).andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(1));
    }

    @Test
    void aPendingRequestIsNotFriendshipYet() throws Exception {
        friendshipRepository.save(Friendship.builder().id(FriendshipId.of(owner, stranger)).requestedBy(stranger)
                .status(FriendshipStatus.PENDING).requestedAt(Instant.now()).build());

        profileOfOwner(stranger).andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(1));
    }

    @Test
    void theFlightListShowsEachViewerOnlyWhatTheyMaySee() throws Exception {
        mockMvc.perform(get("/tailwind/flights/users/" + owner).header("X-User-Id", owner.toString()))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/tailwind/flights/users/" + owner).header("X-User-Id", friend.toString()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].flight.flightNumber", org.hamcrest.Matchers.containsInAnyOrder("LH1", "LH2")));
        mockMvc.perform(get("/tailwind/flights/users/" + owner).header("X-User-Id", stranger.toString()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].flight.flightNumber").value("LH1"));
    }

    @Test
    void theJourneyListIsCutDownToTheVisibleFlights() throws Exception {
        mockMvc.perform(get("/tailwind/journeys/users/" + owner).header("X-User-Id", stranger.toString()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].flights.length()").value(1))
                .andExpect(jsonPath("$[0].flights[0].flight.flightNumber").value("LH1"));
    }

    @Test
    void aJourneyWithNothingVisibleIsNotListedAtAll() throws Exception {
        long secret = journeyRepository.save(Journey.builder().userId(owner).title("Secret trip").createdAt(Instant.now()).build()).getId();
        log(secret, flight("LH9", DAY.plusDays(20), "OTP", "IST", "A320"), Visibility.PRIVATE);

        mockMvc.perform(get("/tailwind/journeys/users/" + owner).header("X-User-Id", stranger.toString()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[*].title", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Secret trip"))));
        mockMvc.perform(get("/tailwind/journeys/users/" + owner).header("X-User-Id", owner.toString()))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void someoneElsesJourneyIsStillNotEditableOrFetchableById() throws Exception {
        long journeyId = journeyRepository.findByUserIdOrderByCreatedAtDescIdDesc(owner).get(0).getId();

        mockMvc.perform(get("/tailwind/journeys/" + journeyId).header("X-User-Id", friend.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/tailwind/journeys/" + journeyId + "/visibility").header("X-User-Id", friend.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aProfileOfSomeoneWhoNeverOptedInIsNotFound() throws Exception {
        Map<String, Object> body = Map.of("query", "query P($u: ID) { travelProfile(userId: $u) { stats { flightCount } } }",
                "variables", Map.of("u", UUID.randomUUID().toString()));

        mockMvc.perform(post(GRAPHQL).header("X-User-Id", stranger.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("NOT_FOUND"));
    }

    @Test
    void aViewerWhoNeverOptedInCannotBrowseAnyoneElse() throws Exception {
        UUID outsider = UUID.randomUUID();

        mockMvc.perform(get("/tailwind/flights/users/" + owner).header("X-User-Id", outsider.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/tailwind/journeys/users/" + owner).header("X-User-Id", outsider.toString()))
                .andExpect(status().isForbidden());
    }
}
