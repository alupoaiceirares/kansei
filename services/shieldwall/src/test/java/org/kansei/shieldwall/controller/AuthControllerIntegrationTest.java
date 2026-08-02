package org.kansei.shieldwall.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.kansei.shieldwall.dto.*;
import org.kansei.shieldwall.model.TokenType;
import org.kansei.shieldwall.model.VerificationToken;
import org.kansei.shieldwall.repository.UserRepository;
import org.kansei.shieldwall.repository.VerificationTokenRepository;
import org.kansei.shieldwall.service.MailEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full HTTP -> service -> real Postgres flow (Testcontainers). MailEventPublisher is mocked - RabbitMQ is an orthogonal infra dependency this test isn't about; see MailEventPublisherIntegrationTest for the real-broker wire contract instead
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // No live broker needed - MailEventPublisher is mocked, but the RabbitTemplate/ConnectionFactory beans still need resolvable (not necessarily reachable) credentials
        registry.add("spring.rabbitmq.username", () -> "test");
        registry.add("spring.rabbitmq.password", () -> "test");
        // Rate limiting is unit-tested separately (RateLimitFilterTest) - this class registers many users across many test methods against the one shared filter instance
        registry.add("app.rate-limit.max-requests", () -> "1000");
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VerificationTokenRepository verificationTokenRepository;
    @MockitoBean
    private MailEventPublisher mailEventPublisher;

    private static int counter = 0;

    private String uniqueEmail() {
        return "user" + (++counter) + "@example.com";
    }

    private String uniqueUsername() {
        return "user" + counter;
    }

    private String verificationTokenFor(UUID userId) {
        return verificationTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(userId) && t.getType() == TokenType.EMAIL_VERIFICATION)
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(VerificationToken::getToken)
                .orElseThrow(() -> new NoSuchElementException("no verification token for user " + userId));
    }

    /** Registers, pulls the verification token straight from the DB, and verifies - returns the auth token. */
    private AuthResponse registerAndVerify(String email, String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, username, password, "First", "Last"))))
                .andExpect(status().isCreated());

        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        String token = verificationTokenFor(userId);

        String body = mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(token))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(body, AuthResponse.class);
    }

    @Test
    void register_createsUnverifiedUser_andPublishesVerificationEvent() throws Exception {
        String email = uniqueEmail();
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, username, "supersecretpw", "First", "Last"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Check your email")));

        assertThat(userRepository.findByEmail(email)).isPresent();
        assertThat(userRepository.findByEmail(email).get().isEmailVerified()).isFalse();
        verify(mailEventPublisher).publishVerificationEmail(any(), any());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = uniqueEmail();
        RegisterRequest first = new RegisterRequest(email, uniqueUsername(), "supersecretpw", null, null);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        RegisterRequest second = new RegisterRequest(email, uniqueUsername(), "supersecretpw", null, null);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_blankEmail_returns400() throws Exception {
        RegisterRequest invalid = new RegisterRequest("", uniqueUsername(), "supersecretpw", null, null);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_beforeVerification_returns401Generic() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, uniqueUsername(), "supersecretpw", null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "supersecretpw"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void verifyEmail_garbageToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest("not-a-real-token"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_thenLogin_succeeds() throws Exception {
        String email = uniqueEmail();
        AuthResponse verifyResponse = registerAndVerify(email, uniqueUsername(), "supersecretpw");
        assertThat(verifyResponse.token()).isNotBlank();

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "supersecretpw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void getCurrentUser_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCurrentUser_returnsProfile() throws Exception {
        String email = uniqueEmail();
        String username = uniqueUsername();
        AuthResponse auth = registerAndVerify(email, username, "supersecretpw");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateProfile_firstNameAndLastName_updatesWithoutTouchingCredentials() throws Exception {
        AuthResponse auth = registerAndVerify(uniqueEmail(), uniqueUsername(), "supersecretpw");

        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(null, null, "NewFirst", "NewLast"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("NewFirst"))
                .andExpect(jsonPath("$.lastName").value("NewLast"));

        // Same token still works - no credentials-affecting field changed.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_emailChange_conflictReturns409() throws Exception {
        String takenEmail = uniqueEmail();
        registerAndVerify(takenEmail, uniqueUsername(), "supersecretpw");
        AuthResponse auth = registerAndVerify(uniqueEmail(), uniqueUsername(), "supersecretpw");

        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(takenEmail, null, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void updateProfile_emailChange_invalidatesOldToken_requiresReLogin() throws Exception {
        String email = uniqueEmail();
        String newEmail = uniqueEmail();
        AuthResponse auth = registerAndVerify(email, uniqueUsername(), "supersecretpw");

        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(newEmail, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));

        // Email is a credential - old JWT is now stale (credentialsVersion bumped).
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isForbidden());

        // New login (old email no longer exists, must use the new one) issues a fresh valid token.
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(newEmail, "supersecretpw"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AuthResponse newAuth = objectMapper.readValue(body, AuthResponse.class);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newAuth.token()))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_requiresAuth() throws Exception {
        // No custom AuthenticationEntryPoint configured - Spring Security's default for a completely unauthenticated request against anyRequest().authenticated() is 403
        mockMvc.perform(put("/api/auth/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("old", "newpassword1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns401() throws Exception {
        String email = uniqueEmail();
        AuthResponse auth = registerAndVerify(email, uniqueUsername(), "supersecretpw");

        mockMvc.perform(put("/api/auth/me/password")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("wrongcurrent", "newpassword1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_correctCurrentPassword_invalidatesOldTokenAndOldPassword() throws Exception {
        String email = uniqueEmail();
        AuthResponse auth = registerAndVerify(email, uniqueUsername(), "supersecretpw");

        mockMvc.perform(put("/api/auth/me/password")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("supersecretpw", "newpassword1"))))
                .andExpect(status().isNoContent());

        // Old JWT is now stale (credentialsVersion bumped) - JwtAuthenticationFilter leaves it unauthenticated, same 403 as no token at all (no custom AuthenticationEntryPoint)
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isForbidden());

        // Old password no longer works, new one does.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "supersecretpw"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "newpassword1"))))
                .andExpect(status().isOk());
    }

    @Test
    void deactivateThenLoginWithinWindow_reactivates() throws Exception {
        String email = uniqueEmail();
        AuthResponse auth = registerAndVerify(email, uniqueUsername(), "supersecretpw");

        mockMvc.perform(post("/api/auth/me/deactivate")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeactivateAccountRequest("supersecretpw"))))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail(email).get().isActive()).isFalse();

        // By design, deactivatedAt was just set, so the retention window is trivially still open - the very next successful login always reactivates immediately (200, not blocked)
        // Login staying blocked past the window is covered at the unit level (UserServiceTest), since backdating deactivatedAt isn't reachable through the API
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "supersecretpw"))))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail(email).get().isActive()).isTrue();
    }

    @Test
    void passwordReset_requestThenConfirm_updatesPassword() throws Exception {
        String email = uniqueEmail();
        registerAndVerify(email, uniqueUsername(), "supersecretpw");

        mockMvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("password reset link")));

        verify(mailEventPublisher).publishPasswordResetEmail(any(), any());

        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        String resetToken = verificationTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(userId) && t.getType() == TokenType.PASSWORD_RESET)
                .findFirst()
                .orElseThrow()
                .getToken();

        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(resetToken, "brandnewpw1"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "brandnewpw1"))))
                .andExpect(status().isOk());
    }

    @Test
    void passwordReset_requestForUnknownEmail_stillReturns200Generic() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("nobody-" + uniqueEmail()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("password reset link")));
    }
}
