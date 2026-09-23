package org.kansei.tailwind.graphql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.model.Flight;
import org.kansei.tailwind.model.FlightSource;
import org.kansei.tailwind.model.Journey;
import org.kansei.tailwind.model.StopType;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.model.Visibility;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.FlightRepository;
import org.kansei.tailwind.repository.JourneyRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.kansei.tailwind.service.AuditPublisher;
import org.kansei.tailwind.service.GeoDistance;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The travel profile query against a real Postgres: stats, countries, collections, records, the period filter,
 * upcoming flights being left out, and one user never seeing another user's flights.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class TravelProfileIntegrationTest {

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

    private static final String PATH = "/tailwind/graphql";
    // Anchored to the real clock: the profile leaves out flights that have not happened yet, so the fixtures must be in the past
    private static final LocalDate DAY = LocalDate.now().minusMonths(6).withDayOfMonth(12);
    private static final LocalDate LATER = DAY.plusMonths(2);
    private static final String DAY_MONTH = DAY.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
    private static final String DAY_ISO = DAY.toString();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TailwindUserRepository tailwindUserRepository;
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

    private UUID user;

    @BeforeEach
    void setUp() {
        userFlightRepository.deleteAll();
        journeyRepository.deleteAll();
        flightRepository.deleteAll();
        tailwindUserRepository.deleteAll();
        user = optedIn();
    }

    private UUID optedIn() {
        UUID id = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(id).joinedAt(Instant.now()).build());
        return id;
    }

    private ResultActions query(UUID viewer, String document, Map<String, Object> variables) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", document);
        if (variables != null) {
            body.put("variables", variables);
        }
        return mockMvc.perform(post(PATH).header("X-User-Id", viewer.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    private ResultActions query(UUID viewer, String document) throws Exception {
        return query(viewer, document, null);
    }

    private long journey() {
        return journeyRepository.save(Journey.builder().userId(user).createdAt(Instant.now()).build()).getId();
    }

    private Flight flight(String number, LocalDate date, String fromIata, String toIata, String typeIcao, String departUtc, String arriveUtc) {
        var departure = airportRepository.findByIata(fromIata).orElseThrow();
        var arrival = airportRepository.findByIata(toIata).orElseThrow();
        var type = typeIcao == null ? null : aircraftTypeRepository.findByIcaoCode(typeIcao).orElseThrow();
        double distance = GeoDistance.haversineKm(departure.getLatitude(), departure.getLongitude(), arrival.getLatitude(), arrival.getLongitude());
        return flightRepository.save(Flight.builder()
                .source(FlightSource.API).flightNumber(number).flightDate(date)
                .airline(airlineRepository.findByIcao("DLH").orElseThrow())
                .departureAirport(departure).arrivalAirport(arrival)
                .aircraftType(type).aircraftFamily(type == null ? null : type.getFamily())
                .departureScheduledUtc(departUtc == null ? null : Instant.parse(departUtc))
                .arrivalScheduledUtc(arriveUtc == null ? null : Instant.parse(arriveUtc))
                .distanceKm(distance).createdAt(Instant.now()).build());
    }

    private void log(UUID owner, long journeyId, Flight flight, Visibility visibility, StopType stopType) {
        userFlightRepository.save(UserFlight.builder().userId(owner).journeyId(journeyId).flight(flight)
                .visibility(visibility).stopType(stopType).createdAt(Instant.now()).build());
    }

    /**
     * Bucharest to Beijing via Istanbul as one journey, plus Frankfurt to New York two months later.
     */
    private void twoJourneys() {
        long viaIstanbul = journey();
        log(user, viaIstanbul, flight("LH1", DAY, "OTP", "IST", "A320", DAY + "T06:00:00Z", DAY + "T08:00:00Z"),
                Visibility.PUBLIC, StopType.LAYOVER);
        log(user, viaIstanbul, flight("LH2", DAY, "IST", "PEK", "A346", DAY + "T11:00:00Z", DAY + "T21:00:00Z"),
                Visibility.PUBLIC, null);

        long transatlantic = journey();
        log(user, transatlantic, flight("LH400", LATER, "FRA", "JFK", "A346", LATER + "T08:55:00Z", LATER + "T17:35:00Z"),
                Visibility.PUBLIC, null);
    }

    @Test
    void statsCoverEveryVisibleFlight() throws Exception {
        twoJourneys();

        query(user, "{ travelProfile { stats { flightCount distanceKm timeInAirMinutes flightsWithDuration countryCount airportCount airlineCount aircraftFamilyCount aircraftTypeCount cargoFlightCount longestFlightKm } } }")
                .andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(3))
                .andExpect(jsonPath("$.data.travelProfile.stats.timeInAirMinutes").value(120 + 600 + 520))
                .andExpect(jsonPath("$.data.travelProfile.stats.flightsWithDuration").value(3))
                .andExpect(jsonPath("$.data.travelProfile.stats.airportCount").value(5))
                .andExpect(jsonPath("$.data.travelProfile.stats.airlineCount").value(1))
                .andExpect(jsonPath("$.data.travelProfile.stats.aircraftFamilyCount").value(2))
                .andExpect(jsonPath("$.data.travelProfile.stats.aircraftTypeCount").value(2))
                .andExpect(jsonPath("$.data.travelProfile.stats.cargoFlightCount").value(0))
                .andExpect(jsonPath("$.data.travelProfile.stats.countryCount").value(4))
                .andExpect(jsonPath("$.data.travelProfile.stats.longestFlightKm").value(org.hamcrest.Matchers.greaterThan(6000.0)));
    }

    @Test
    void countriesSeparateTheLayoverFromTheRest() throws Exception {
        twoJourneys();

        query(user, "{ travelProfile { countries { visited { code name continent visitCount } passedThrough { code name } } } }")
                .andExpect(jsonPath("$.data.travelProfile.countries.visited[*].code",
                        org.hamcrest.Matchers.containsInAnyOrder("RO", "CN", "DE", "US")))
                .andExpect(jsonPath("$.data.travelProfile.countries.passedThrough[*].code", org.hamcrest.Matchers.contains("TR")))
                .andExpect(jsonPath("$.data.travelProfile.countries.passedThrough[0].name").value("Turkey"));
    }

    @Test
    void theAircraftCollectionGroupsByFamilyAndExpandsIntoVariants() throws Exception {
        twoJourneys();

        query(user, "{ travelProfile { aircraft { family flightCount photoUrl variants { icaoCode name flightCount photoUrl } } } }")
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].family").value("A340"))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].flightCount").value(2))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].photoUrl").value("/tailwind/aircraft-families/photo?name=A340"))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].variants[0].icaoCode").value("A346"))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].variants[0].flightCount").value(2))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].variants[0].photoUrl",
                        org.hamcrest.Matchers.startsWith("/tailwind/aircraft-types/")))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[1].family").value("A320 family"));
    }

    @Test
    void aFlightWithOnlyAFamilyShowsAsVariantUnknown() throws Exception {
        long trip = journey();
        Flight familyOnly = flight("LH9", DAY, "OTP", "FRA", null, null, null);
        familyOnly.setAircraftFamily("A340");
        flightRepository.save(familyOnly);
        log(user, trip, familyOnly, Visibility.PUBLIC, null);
        log(user, trip, flight("LH10", DAY.plusDays(1), "FRA", "OTP", "A346", null, null), Visibility.PUBLIC, null);

        query(user, "{ travelProfile { aircraft { family flightCount variants { name icaoCode flightCount } } } }")
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].flightCount").value(2))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].variants[1].name").value("Variant unknown"))
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].variants[1].icaoCode").doesNotExist())
                .andExpect(jsonPath("$.data.travelProfile.aircraft[0].variants[1].flightCount").value(1));
    }

    @Test
    void airportsAndAirlinesComeBackWithTheirCountsAndCoordinates() throws Exception {
        twoJourneys();

        query(user, "{ travelProfile { airports { iata timesUsed departures arrivals latitude longitude firstVisit } airlines { icao name flightCount } } }")
                .andExpect(jsonPath("$.data.travelProfile.airports[?(@.iata=='IST')].timesUsed", org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.data.travelProfile.airports[?(@.iata=='IST')].departures", org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.data.travelProfile.airports[?(@.iata=='IST')].arrivals", org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.data.travelProfile.airports[?(@.iata=='OTP')].firstVisit", org.hamcrest.Matchers.contains(DAY_ISO)))
                .andExpect(jsonPath("$.data.travelProfile.airlines[0].icao").value("DLH"))
                .andExpect(jsonPath("$.data.travelProfile.airlines[0].flightCount").value(3));
    }

    @Test
    void recordsPickTheRightFlightsAndPeriods() throws Exception {
        twoJourneys();

        query(user, "{ travelProfile { records { longestFlight { flightNumber distanceKm arrivalIata } shortestFlight { flightNumber } firstFlight { date } longestJourney { title flightCount } mostFlownRoute { flightCount } mostFlownAirline { name count } mostFlownAircraftFamily { name count } busiestMonth { label count } biggestYear { label count } } } }")
                .andExpect(jsonPath("$.data.travelProfile.records.longestFlight.arrivalIata").value("PEK"))
                .andExpect(jsonPath("$.data.travelProfile.records.shortestFlight.flightNumber").value("LH1"))
                .andExpect(jsonPath("$.data.travelProfile.records.firstFlight.date").value(DAY_ISO))
                .andExpect(jsonPath("$.data.travelProfile.records.longestJourney.title").value("OTP - PEK"))
                .andExpect(jsonPath("$.data.travelProfile.records.longestJourney.flightCount").value(2))
                .andExpect(jsonPath("$.data.travelProfile.records.mostFlownAirline.count").value(3))
                .andExpect(jsonPath("$.data.travelProfile.records.mostFlownAircraftFamily.name").value("A340"))
                .andExpect(jsonPath("$.data.travelProfile.records.busiestMonth.label").value(DAY_MONTH))
                .andExpect(jsonPath("$.data.travelProfile.records.busiestMonth.count").value(2))
                .andExpect(jsonPath("$.data.travelProfile.records.biggestYear.label").value(String.valueOf(DAY.getYear())));
    }

    @Test
    void theExtraRecordsComeBackWithTheRest() throws Exception {
        twoJourneys();

        // Istanbul is the airport used twice, so it is home, and New York is the furthest point from it
        query(user, "{ travelProfile { records { furthestPoint { iata homeIata distanceFromHomeKm } longestGap { days } mostAircraftInAJourney { aircraftCount flightCount } highestCabin { cabinClass } } } }")
                .andExpect(jsonPath("$.data.travelProfile.records.furthestPoint.iata").value("JFK"))
                .andExpect(jsonPath("$.data.travelProfile.records.furthestPoint.homeIata").value("IST"))

                .andExpect(jsonPath("$.data.travelProfile.records.mostAircraftInAJourney.aircraftCount").value(2))
                .andExpect(jsonPath("$.data.travelProfile.records.highestCabin").doesNotExist());
    }

    @Test
    void theAircraftCatalogListsFamiliesTheUserHasNeverFlown() throws Exception {
        twoJourneys();

        query(user, "{ travelProfile { aircraftCatalog { family flightCount variantCount photoUrl } } }")
                .andExpect(jsonPath("$.data.travelProfile.aircraftCatalog[?(@.family=='A340')].flightCount", org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.data.travelProfile.aircraftCatalog[?(@.family=='A380')].flightCount", org.hamcrest.Matchers.contains(0)));
    }

    @Test
    void breakdownsSayHowMuchOfTheLogTheyCover() throws Exception {
        twoJourneys();

        query(user, "{ travelProfile { breakdowns { cabinClasses { name count } reasons { name count } flightsPerYear { label count } flightsWithCabin } } }")
                .andExpect(jsonPath("$.data.travelProfile.breakdowns.flightsWithCabin").value(0))
                .andExpect(jsonPath("$.data.travelProfile.breakdowns.cabinClasses.length()").value(0))
                .andExpect(jsonPath("$.data.travelProfile.breakdowns.flightsPerYear[0].count").value(3));
    }

    @Test
    void thePeriodNarrowsEveryField() throws Exception {
        twoJourneys();

        query(user, "query Profile($p: PeriodInput) { travelProfile(period: $p) { stats { flightCount } countries { visited { code } } aircraft { family } } }",
                Map.of("p", Map.of("from", LATER.withDayOfMonth(1).toString(), "to", LATER.withDayOfMonth(28).toString())))
                .andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(1))
                .andExpect(jsonPath("$.data.travelProfile.countries.visited[*].code", org.hamcrest.Matchers.containsInAnyOrder("DE", "US")))
                .andExpect(jsonPath("$.data.travelProfile.aircraft.length()").value(1));
    }

    @Test
    void aBackwardsPeriodIsRejected() throws Exception {
        Map<String, Object> body = Map.of("query", "query Profile($p: PeriodInput) { travelProfile(period: $p) { stats { flightCount } } }",
                "variables", Map.of("p", Map.of("from", LATER.withDayOfMonth(28).toString(), "to", LATER.withDayOfMonth(1).toString())));

        mockMvc.perform(post(PATH).header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("must not be after")))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
    }

    @Test
    void flightsThatHaveNotHappenedYetAreLeftOut() throws Exception {
        long trip = journey();
        log(user, trip, flight("LH1", DAY, "OTP", "FRA", "A320", DAY + "T06:00:00Z", DAY + "T08:00:00Z"), Visibility.PUBLIC, null);
        LocalDate future = LocalDate.now().plusMonths(3);
        log(user, trip, flight("LH99", future, "FRA", "JFK", "A346", null, null), Visibility.PUBLIC, null);

        query(user, "{ travelProfile { stats { flightCount } records { longestFlight { flightNumber } } } }")
                .andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(1))
                .andExpect(jsonPath("$.data.travelProfile.records.longestFlight.flightNumber").value("LH1"));
    }

    @Test
    void anEmptyProfileAnswersWithZerosAndNulls() throws Exception {
        query(user, "{ travelProfile { stats { flightCount distanceKm longestFlightKm } countries { visited { code } } aircraft { family } airports { iata } records { longestFlight { flightNumber } busiestMonth { label } } } }")
                .andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(0))
                .andExpect(jsonPath("$.data.travelProfile.stats.distanceKm").value(0.0))
                .andExpect(jsonPath("$.data.travelProfile.stats.longestFlightKm").doesNotExist())
                .andExpect(jsonPath("$.data.travelProfile.countries.visited.length()").value(0))
                .andExpect(jsonPath("$.data.travelProfile.aircraft.length()").value(0))
                .andExpect(jsonPath("$.data.travelProfile.airports.length()").value(0))
                .andExpect(jsonPath("$.data.travelProfile.records.longestFlight").doesNotExist())
                .andExpect(jsonPath("$.data.travelProfile.records.busiestMonth").doesNotExist());
    }

    @Test
    void theProfileNeedsAnOptedInCallerAndAUserIdHeader() throws Exception {
        String document = "{\"query\":\"{ travelProfile { stats { flightCount } } }\"}";

        mockMvc.perform(post(PATH).header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].message").value("Not opted into tailwind"))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"));

        // No header at all never reaches a resolver, the interceptor rejects the request before any data is built
        String withoutHeader = mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(document))
                .andReturn().getResponse().getContentAsString();
        assertThat(withoutHeader).doesNotContain("travelProfile").doesNotContain("flightCount");
    }

    @Test
    void aPrivateOrFriendsFlightNeverLeavesItsOwnersProfile() throws Exception {
        // The owner's own totals include everything, and nobody else can reach these rows at all yet
        long trip = journey();
        log(user, trip, flight("LH1", DAY, "OTP", "FRA", "A320", null, null), Visibility.PUBLIC, null);
        log(user, trip, flight("LH2", DAY, "FRA", "JFK", "A346", null, null), Visibility.PRIVATE, null);
        log(user, trip, flight("LH3", DAY, "JFK", "OTP", "A346", null, null), Visibility.FRIENDS, null);

        query(user, "{ travelProfile { stats { flightCount } } }")
                .andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(3));

        UUID other = optedIn();
        query(other, "{ travelProfile { stats { flightCount } } }")
                .andExpect(jsonPath("$.data.travelProfile.stats.flightCount").value(0));
        assertThat(userFlightRepository.findDetailedByUserId(other)).isEmpty();
    }
}
