package org.kansei.tailwind.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.model.FriendshipId;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.FriendshipRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Friend search, requests and the friend list against a real Postgres, including who is allowed to answer
 * a request and what happens when both sides ask at once.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class FriendshipIntegrationTest {

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
    private TailwindUserRepository tailwindUserRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private FriendshipService friendshipService;
    @Autowired
    private ViewerAccess viewerAccess;
    @Autowired
    private UserDataCleaner userDataCleaner;

    @MockitoBean
    private AuditPublisher auditPublisher;
    @MockitoBean
    private ShieldwallUserClient shieldwallUserClient;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        friendshipRepository.deleteAll();
        tailwindUserRepository.deleteAll();
        alice = optedIn();
        bob = optedIn();
        when(shieldwallUserClient.resolveUsernames(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<UUID> ids = invocation.getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> "user-" + id.toString().substring(0, 4)));
        });
    }

    private UUID optedIn() {
        UUID id = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(id).joinedAt(Instant.now()).build());
        return id;
    }

    private static MockHttpServletRequestBuilder as(UUID who, MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", who.toString());
    }

    private void befriend(UUID one, UUID other) throws Exception {
        mockMvc.perform(as(one, post("/tailwind/friends/requests/" + other))).andExpect(status().isNoContent());
        mockMvc.perform(as(other, post("/tailwind/friends/requests/" + one + "/accept"))).andExpect(status().isNoContent());
    }

    @Test
    void aRequestIsAcceptedByTheOtherSideAndBothBecomeFriends() throws Exception {
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob))).andExpect(status().isNoContent());

        mockMvc.perform(as(bob, get("/tailwind/friends/requests")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(alice.toString()))
                .andExpect(jsonPath("$[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$[0].username").value(org.hamcrest.Matchers.startsWith("user-")));
        mockMvc.perform(as(alice, get("/tailwind/friends/requests")))
                .andExpect(jsonPath("$[0].direction").value("OUTGOING"));
        mockMvc.perform(as(alice, get("/tailwind/friends"))).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(as(bob, post("/tailwind/friends/requests/" + alice + "/accept"))).andExpect(status().isNoContent());

        mockMvc.perform(as(alice, get("/tailwind/friends")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(bob.toString()))
                .andExpect(jsonPath("$[0].friendsSince").exists());
        mockMvc.perform(as(bob, get("/tailwind/friends"))).andExpect(jsonPath("$[0].userId").value(alice.toString()));
        mockMvc.perform(as(bob, get("/tailwind/friends/requests"))).andExpect(jsonPath("$.length()").value(0));
        assertThat(viewerAccess.areFriends(alice, bob)).isTrue();
    }

    @Test
    void onePairIsOneRowWhicheverWayRoundItIsAsked() throws Exception {
        befriend(alice, bob);

        assertThat(friendshipRepository.count()).isEqualTo(1);
        assertThat(friendshipRepository.findById(FriendshipId.of(alice, bob))).isPresent();
        assertThat(friendshipRepository.findById(FriendshipId.of(bob, alice))).isPresent();
    }

    @Test
    void askingBackSomeoneWhoAlreadyAskedYouAcceptsInstead() throws Exception {
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob))).andExpect(status().isNoContent());

        mockMvc.perform(as(bob, post("/tailwind/friends/requests/" + alice))).andExpect(status().isNoContent());

        assertThat(viewerAccess.areFriends(alice, bob)).isTrue();
        assertThat(friendshipRepository.count()).isEqualTo(1);
    }

    @Test
    void nobodyCanAnswerTheirOwnRequestOrOneThatDoesNotExist() throws Exception {
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob))).andExpect(status().isNoContent());

        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob + "/accept"))).andExpect(status().isForbidden());
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob + "/decline"))).andExpect(status().isForbidden());
        UUID stranger = optedIn();
        mockMvc.perform(as(stranger, post("/tailwind/friends/requests/" + bob + "/accept"))).andExpect(status().isNotFound());

        assertThat(viewerAccess.areFriends(alice, bob)).isFalse();
    }

    @Test
    void aSecondRequestOrBefriendingYourselfIsRefused() throws Exception {
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob))).andExpect(status().isNoContent());

        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob))).andExpect(status().isConflict());
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + alice))).andExpect(status().isBadRequest());

        mockMvc.perform(as(bob, post("/tailwind/friends/requests/" + alice + "/accept"))).andExpect(status().isNoContent());
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob))).andExpect(status().isConflict());
    }

    @Test
    void decliningAndUnfriendingRemoveTheRow() throws Exception {
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob))).andExpect(status().isNoContent());
        mockMvc.perform(as(bob, post("/tailwind/friends/requests/" + alice + "/decline"))).andExpect(status().isNoContent());
        assertThat(friendshipRepository.count()).isZero();

        befriend(alice, bob);
        mockMvc.perform(as(bob, delete("/tailwind/friends/" + alice))).andExpect(status().isNoContent());

        assertThat(friendshipRepository.count()).isZero();
        assertThat(viewerAccess.areFriends(alice, bob)).isFalse();
        // and a request can be withdrawn the same way
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob))).andExpect(status().isNoContent());
        mockMvc.perform(as(alice, delete("/tailwind/friends/" + bob))).andExpect(status().isNoContent());
        assertThat(friendshipRepository.count()).isZero();
        mockMvc.perform(as(alice, delete("/tailwind/friends/" + bob))).andExpect(status().isNotFound());
    }

    @Test
    void searchSkipsYourselfAndUsersWhoNeverOptedIn() throws Exception {
        UUID notOptedIn = UUID.randomUUID();
        when(shieldwallUserClient.searchUsers(any(), anyInt())).thenReturn(List.of(
                new ShieldwallUserClient.UserMatch(alice, "alice"),
                new ShieldwallUserClient.UserMatch(bob, "bob"),
                new ShieldwallUserClient.UserMatch(notOptedIn, "ghost")));

        mockMvc.perform(as(alice, get("/tailwind/friends/search").param("q", "al")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(bob.toString()))
                .andExpect(jsonPath("$[0].relation").value("NONE"));
    }

    @Test
    void searchShowsHowYouAlreadyRelateToEachResult() throws Exception {
        UUID carol = optedIn();
        when(shieldwallUserClient.searchUsers(any(), anyInt())).thenReturn(List.of(
                new ShieldwallUserClient.UserMatch(bob, "bob"),
                new ShieldwallUserClient.UserMatch(carol, "carol")));
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + bob))).andExpect(status().isNoContent());
        mockMvc.perform(as(carol, post("/tailwind/friends/requests/" + alice))).andExpect(status().isNoContent());

        mockMvc.perform(as(alice, get("/tailwind/friends/search").param("q", "us")))
                .andExpect(jsonPath("$[?(@.userId=='" + bob + "')].relation", org.hamcrest.Matchers.contains("REQUEST_SENT")))
                .andExpect(jsonPath("$[?(@.userId=='" + carol + "')].relation", org.hamcrest.Matchers.contains("REQUEST_RECEIVED")));

        mockMvc.perform(as(bob, post("/tailwind/friends/requests/" + alice + "/accept"))).andExpect(status().isNoContent());
        mockMvc.perform(as(alice, get("/tailwind/friends/search").param("q", "us")))
                .andExpect(jsonPath("$[?(@.userId=='" + bob + "')].relation", org.hamcrest.Matchers.contains("FRIENDS")));
    }

    @Test
    void searchNeedsARealQueryAndAnOptedInCaller() throws Exception {
        mockMvc.perform(as(alice, get("/tailwind/friends/search").param("q", "a"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(UUID.randomUUID(), get("/tailwind/friends/search").param("q", "bob"))).andExpect(status().isForbidden());
        mockMvc.perform(as(UUID.randomUUID(), get("/tailwind/friends"))).andExpect(status().isForbidden());
    }

    @Test
    void aRequestToSomeoneWhoNeverOptedInIsNotFound() throws Exception {
        mockMvc.perform(as(alice, post("/tailwind/friends/requests/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void shieldwallBeingDownStillListsFriendsWithoutTheirNames() throws Exception {
        befriend(alice, bob);
        when(shieldwallUserClient.resolveUsernames(anyCollection())).thenReturn(Map.of());

        mockMvc.perform(as(alice, get("/tailwind/friends")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(bob.toString()))
                .andExpect(jsonPath("$[0].username").doesNotExist());
    }

    @Test
    void purgingAnAccountRemovesItsFriendshipsToo() throws Exception {
        befriend(alice, bob);

        userDataCleaner.deleteAllFor(List.of(alice));

        assertThat(friendshipRepository.count()).isZero();
        assertThat(friendshipService.friends(bob)).isEmpty();
    }
}
