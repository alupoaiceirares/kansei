package org.kansei.shieldwall.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kansei.shieldwall.dto.*;
import org.kansei.shieldwall.exception.EmailAlreadyExistsException;
import org.kansei.shieldwall.exception.InvalidCredentialsException;
import org.kansei.shieldwall.exception.InvalidOrExpiredTokenException;
import org.kansei.shieldwall.exception.UserNotFoundException;
import org.kansei.shieldwall.exception.UsernameAlreadyExistsException;
import org.kansei.shieldwall.model.TokenType;
import org.kansei.shieldwall.model.User;
import org.kansei.shieldwall.model.VerificationToken;
import org.kansei.shieldwall.repository.UserRepository;
import org.kansei.shieldwall.repository.VerificationTokenRepository;
import org.kansei.shieldwall.security.JwtService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationTokenRepository verificationTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private MailEventPublisher mailEventPublisher;
    // Never stubbed - publishCredentialsVersion swallows any exception, including the NPE an unstubbed
    // opsForValue() chain would throw, so tests don't need to care about Redis at all
    @Mock
    private StringRedisTemplate redisTemplate;

    private UserService userService;

    private static final int RETENTION_MONTHS = 3;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, verificationTokenRepository, passwordEncoder, jwtService, mailEventPublisher, redisTemplate);
        ReflectionTestUtils.setField(userService, "retentionMonths", RETENTION_MONTHS);
        ReflectionTestUtils.setField(userService, "verificationExpiryHours", 24);
        ReflectionTestUtils.setField(userService, "passwordResetExpiryMinutes", 60);
    }

    private User baseUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .username("someuser")
                .password("hashed-password")
                .active(true)
                .emailVerified(true)
                .credentialsVersion(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ---- register ----

    @Test
    void register_savesUnverifiedUserAndSendsVerificationEmail_noJwtIssued() {
        RegisterRequest request = new RegisterRequest("new@example.com", "newuser", "supersecretpw", "First", "Last");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        MessageResponse response = userService.register(request);

        assertThat(response.message()).contains("Check your email to verify");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().isActive()).isTrue();
        assertThat(savedUser.getValue().isEmailVerified()).isFalse();
        assertThat(savedUser.getValue().getPassword()).isEqualTo("encoded");

        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(mailEventPublisher).publishVerificationEmail(any(User.class), anyString());
        verifyNoInteractions(jwtService);
    }

    @Test
    void register_emailAlreadyExists_throwsAndNeverSaves() {
        RegisterRequest request = new RegisterRequest("dup@example.com", "newuser", "supersecretpw", null, null);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request)).isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(mailEventPublisher);
    }

    @Test
    void register_usernameAlreadyExists_throwsAndNeverSaves() {
        RegisterRequest request = new RegisterRequest("new@example.com", "dupuser", "supersecretpw", null, null);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByUsername(request.username())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request)).isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_mixedCaseEmail_normalizedToLowercaseBeforeCheckAndSave() {
        RegisterRequest request = new RegisterRequest("  User@EXAMPLE.com  ", "newuser", "supersecretpw", null, null);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.register(request);

        verify(userRepository).existsByEmail("user@example.com");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("user@example.com");
    }

    // ---- login ----

    @Test
    void login_wrongPassword_throwsGenericInvalidCredentials() {
        User user = baseUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userService.login(new LoginRequest(user.getEmail(), "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_unknownEmail_throwsGenericInvalidCredentials() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void login_deactivatedWithinRetentionWindow_reactivatesAndSucceeds() {
        User user = baseUser();
        user.setActive(false);
        user.setDeactivatedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = userService.login(new LoginRequest(user.getEmail(), "correct"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(user.isActive()).isTrue();
        assertThat(user.getDeactivatedAt()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void login_deactivatedPastRetentionWindow_throwsAndDoesNotReactivate() {
        User user = baseUser();
        user.setActive(false);
        user.setDeactivatedAt(Instant.now().minus(RETENTION_MONTHS * 31L + 5, ChronoUnit.DAYS));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> userService.login(new LoginRequest(user.getEmail(), "correct")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_unverifiedEmail_throwsGenericInvalidCredentials_doesNotLeakCorrectPassword() {
        User user = baseUser();
        user.setEmailVerified(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> userService.login(new LoginRequest(user.getEmail(), "correct")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(jwtService);
    }

    @Test
    void login_activeAndVerified_succeeds() {
        User user = baseUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = userService.login(new LoginRequest(user.getEmail(), "correct"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.username()).isEqualTo(user.getUsername());
    }

    @Test
    void login_mixedCaseEmail_stillMatchesStoredLowercaseEmail() {
        User user = baseUser();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = userService.login(new LoginRequest("  User@EXAMPLE.com  ", "correct"));

        assertThat(response.token()).isEqualTo("jwt-token");
    }

    // ---- getCurrentUser ----

    @Test
    void getCurrentUser_found_returnsMappedResponse() {
        User user = baseUser();
        user.setFirstName("First");
        user.setLastName("Last");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserResponse response = userService.getCurrentUser(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.username()).isEqualTo(user.getUsername());
        assertThat(response.firstName()).isEqualTo("First");
        assertThat(response.lastName()).isEqualTo("Last");
        assertThat(response.active()).isTrue();
    }

    @Test
    void getCurrentUser_notFound_throws() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ---- updateProfile ----

    @Test
    void updateProfile_notFound_throws() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(userId, new UpdateProfileRequest(null, null, null, null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateProfile_allFieldsNull_leavesUserUnchanged() {
        User user = baseUser();
        user.setFirstName("First");
        user.setLastName("Last");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(user.getId(), new UpdateProfileRequest(null, null, null, null));

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getUsername()).isEqualTo("someuser");
        assertThat(user.getFirstName()).isEqualTo("First");
        assertThat(user.getLastName()).isEqualTo("Last");
        assertThat(user.getCredentialsVersion()).isZero();
    }

    @Test
    void updateProfile_emailChangeToExistingEmail_throwsAndDoesNotSave() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(user.getId(),
                new UpdateProfileRequest("taken@example.com", null, null, null)))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_emailChangeToNewEmail_updatesAndBumpsCredentialsVersion() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(user.getId(), new UpdateProfileRequest("new@example.com", null, null, null));

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getCredentialsVersion()).isEqualTo(1);
    }

    @Test
    void updateProfile_emailUnchanged_doesNotBumpCredentialsVersion() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        // Same value as the user already has - should be treated as no-op, not a "change"
        userService.updateProfile(user.getId(), new UpdateProfileRequest(user.getEmail(), null, null, null));

        assertThat(user.getCredentialsVersion()).isZero();
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateProfile_emailSameValueDifferentCase_treatedAsNoChange() {
        User user = baseUser(); // email = "user@example.com"
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(user.getId(), new UpdateProfileRequest("User@EXAMPLE.com", null, null, null));

        assertThat(user.getCredentialsVersion()).isZero();
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateProfile_usernameChangeToExistingUsername_throwsAndDoesNotSave() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(user.getId(),
                new UpdateProfileRequest(null, "taken", null, null)))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_usernameChangeToNewUsername_updatesWithoutBumpingCredentialsVersion() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("newname")).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(user.getId(), new UpdateProfileRequest(null, "newname", null, null));

        assertThat(user.getUsername()).isEqualTo("newname");
        assertThat(user.getCredentialsVersion()).isZero();
    }

    @Test
    void updateProfile_firstNameAndLastNameProvided_updatesBoth() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(user.getId(), new UpdateProfileRequest(null, null, "NewFirst", "NewLast"));

        assertThat(user.getFirstName()).isEqualTo("NewFirst");
        assertThat(user.getLastName()).isEqualTo("NewLast");
    }

    // ---- changePassword ----

    @Test
    void changePassword_wrongCurrentPassword_throws() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(user.getId(), new ChangePasswordRequest("wrong", "newpassword1")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_correctCurrentPassword_updatesAndBumpsCredentialsVersion() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-encoded");

        userService.changePassword(user.getId(), new ChangePasswordRequest("correct", "newpassword1"));

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        assertThat(user.getCredentialsVersion()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    // ---- deactivateAccount ----

    @Test
    void deactivateAccount_wrongPassword_throwsAndDoesNotDeactivate() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userService.deactivateAccount(user.getId(), new DeactivateAccountRequest("wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void deactivateAccount_correctPassword_deactivatesAndBumpsCredentialsVersion() {
        User user = baseUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);

        userService.deactivateAccount(user.getId(), new DeactivateAccountRequest("correct"));

        assertThat(user.isActive()).isFalse();
        assertThat(user.getDeactivatedAt()).isNotNull();
        assertThat(user.getCredentialsVersion()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    // ---- verifyEmail ----

    @Test
    void verifyEmail_unknownToken_throws() {
        when(verificationTokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.verifyEmail(new VerifyEmailRequest("bad-token")))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void verifyEmail_wrongTokenType_throws() {
        User user = baseUser();
        VerificationToken token = VerificationToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token("tok")
                .type(TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(verificationTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> userService.verifyEmail(new VerifyEmailRequest("tok")))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void verifyEmail_expiredToken_throws() {
        User user = baseUser();
        VerificationToken token = VerificationToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token("tok")
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().minusSeconds(10))
                .build();
        when(verificationTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> userService.verifyEmail(new VerifyEmailRequest("tok")))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void verifyEmail_alreadyUsedToken_throws() {
        User user = baseUser();
        VerificationToken token = VerificationToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token("tok")
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plusSeconds(3600))
                .usedAt(Instant.now().minusSeconds(60))
                .build();
        when(verificationTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> userService.verifyEmail(new VerifyEmailRequest("tok")))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void verifyEmail_validToken_marksVerifiedConsumesTokenAndAutoLogsIn() {
        User user = baseUser();
        user.setEmailVerified(false);
        VerificationToken token = VerificationToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token("tok")
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(verificationTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = userService.verifyEmail(new VerifyEmailRequest("tok"));

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token");
        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(token);
    }

    // ---- resendVerification ----

    @Test
    void resendVerification_unknownEmail_genericResponse_noTokenCreated() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        MessageResponse response = userService.resendVerification(new ResendVerificationRequest("nobody@example.com"));

        assertThat(response.message()).contains("If that email is registered");
        verifyNoInteractions(mailEventPublisher);
        verify(verificationTokenRepository, never()).save(any());
    }

    @Test
    void resendVerification_alreadyVerified_genericResponse_noTokenCreated() {
        User user = baseUser();
        user.setEmailVerified(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        userService.resendVerification(new ResendVerificationRequest(user.getEmail()));

        verifyNoInteractions(mailEventPublisher);
    }

    @Test
    void resendVerification_unverified_createsTokenAndSendsEmail() {
        User user = baseUser();
        user.setEmailVerified(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        userService.resendVerification(new ResendVerificationRequest(user.getEmail()));

        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(mailEventPublisher).publishVerificationEmail(eq(user), anyString());
    }

    // ---- requestPasswordReset ----

    @Test
    void requestPasswordReset_unknownEmail_genericResponse_noTokenCreated() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        MessageResponse response = userService.requestPasswordReset(new PasswordResetRequest("nobody@example.com"));

        assertThat(response.message()).contains("password reset link");
        verifyNoInteractions(mailEventPublisher);
    }

    @Test
    void requestPasswordReset_knownEmail_createsTokenAndSendsEmail() {
        User user = baseUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        userService.requestPasswordReset(new PasswordResetRequest(user.getEmail()));

        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(mailEventPublisher).publishPasswordResetEmail(eq(user), anyString());
    }

    // ---- confirmPasswordReset ----

    @Test
    void confirmPasswordReset_wrongTokenType_throws() {
        User user = baseUser();
        VerificationToken token = VerificationToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token("tok")
                .type(TokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(verificationTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> userService.confirmPasswordReset(new PasswordResetConfirmRequest("tok", "newpassword1")))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void confirmPasswordReset_validToken_updatesPasswordBumpsVersionAndConsumesToken() {
        User user = baseUser();
        VerificationToken token = VerificationToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token("tok")
                .type(TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(verificationTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-encoded");

        userService.confirmPasswordReset(new PasswordResetConfirmRequest("tok", "newpassword1"));

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        assertThat(user.getCredentialsVersion()).isEqualTo(1);
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(token);
    }

    // ---- purgeExpiredDeactivatedAccounts ----

    @Test
    void purgeExpiredDeactivatedAccounts_deletesExpiredUsersAndExpiredTokens() {
        User expiredUser = baseUser();
        when(userRepository.findByActiveFalseAndDeactivatedAtBefore(any(Instant.class)))
                .thenReturn(java.util.List.of(expiredUser));

        userService.purgeExpiredDeactivatedAccounts();

        verify(userRepository).deleteAll(java.util.List.of(expiredUser));
        verify(verificationTokenRepository).deleteByExpiresAtBefore(any(Instant.class));
    }

    // ---- logout ----

    @Test
    void logout_validToken_blacklistsJtiWithRemainingTtl() {
        String token = "some.jwt.token";
        Instant expiresAt = Instant.now().plusSeconds(120);
        when(jwtService.extractJti(token)).thenReturn("jti-123");
        when(jwtService.extractExpiration(token)).thenReturn(expiresAt);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOperations = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        userService.logout(token);

        ArgumentCaptor<java.time.Duration> ttlCaptor = ArgumentCaptor.forClass(java.time.Duration.class);
        verify(valueOperations).set(eq("shieldwall:blacklisted-jti:jti-123"), eq("1"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().toSeconds()).isBetween(115L, 120L);
    }

    @Test
    void logout_alreadyExpiredToken_doesNotWriteToRedis() {
        String token = "some.jwt.token";
        when(jwtService.extractJti(token)).thenReturn("jti-123");
        when(jwtService.extractExpiration(token)).thenReturn(Instant.now().minusSeconds(5));

        userService.logout(token);

        verify(redisTemplate, never()).opsForValue();
    }
}
