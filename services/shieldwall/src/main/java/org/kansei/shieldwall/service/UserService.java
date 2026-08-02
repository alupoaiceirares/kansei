package org.kansei.shieldwall.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailEventPublisher mailEventPublisher;

    @Value("${account.deactivation.retention-months:3}")
    private int retentionMonths;

    @Value("${app.mail.email-verification-expiry-hours:24}")
    private int verificationExpiryHours;

    @Value("${app.mail.password-reset-expiry-minutes:60}")
    private int passwordResetExpiryMinutes;

    public UserService(UserRepository userRepository, VerificationTokenRepository verificationTokenRepository,
                        PasswordEncoder passwordEncoder, JwtService jwtService, MailEventPublisher mailEventPublisher) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailEventPublisher = mailEventPublisher;
    }

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        User user = User.builder()
                .email(request.email())
                .username(request.username())
                // Never store the raw password - always through the encoder first.
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .active(true)
                .build();

        User saved = userRepository.save(user);
        createAndSendToken(saved, TokenType.EMAIL_VERIFICATION);

        // No JWT here - login is blocked until the confirmation link is clicked.
        return new MessageResponse("Registration successful. Check your email to verify your account before logging in.");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        // Password checked before touching active/deactivatedAt - never reveal account state to someone who isn't logged in
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            if (user.getDeactivatedAt() != null && withinRetentionWindow(user.getDeactivatedAt())) {
                // Still within the retention window - logging back in undoes the deactivation.
                user.setActive(true);
                user.setDeactivatedAt(null);
                userRepository.save(user);
            } else {
                throw new InvalidCredentialsException();
            }
        }

        // Same generic error as wrong-password/unknown-email/disabled - don't reveal that the
        // password was actually correct to someone probing an unverified account.
        if (!user.isEmailVerified()) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getUsername());
    }

    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // Only touch fields that were actually provided - null means "unchanged".
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new EmailAlreadyExistsException(request.email());
            }
            user.setEmail(request.email());
            // Email is a login credential - invalidate existing tokens, force re-login.
            user.setCredentialsVersion(user.getCredentialsVersion() + 1);
        }

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new UsernameAlreadyExistsException(request.username());
            }
            user.setUsername(request.username());
        }

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }

        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }

        // @PreUpdate on the entity sets updatedAt automatically on save.
        User saved = userRepository.save(user);
        return toUserResponse(saved);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // Require proof of the current password even though the request is
        // already JWT-authenticated - protects against a leaked/stolen token
        // being used to lock the real owner out.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        // Invalidate existing tokens - force re-login with the new password.
        user.setCredentialsVersion(user.getCredentialsVersion() + 1);
        userRepository.save(user);
    }

    @Transactional
    public void deactivateAccount(UUID userId, DeactivateAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // Require the current password even though the request is already JWT-authenticated same reasoning as changePassword: protects against a leaked/stolen token.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        user.setActive(false);
        user.setDeactivatedAt(Instant.now());
        // Invalidate existing tokens immediately, not just at natural expiry.
        user.setCredentialsVersion(user.getCredentialsVersion() + 1);
        userRepository.save(user);
    }

    /**
     * Hard-deletes accounts deactivated for longer than the retention window. Invoked by AccountPurgeScheduler on a schedule.
     */
    @Transactional
    public void purgeExpiredDeactivatedAccounts() {
        Instant cutoff = Instant.now().atZone(ZoneOffset.UTC).minusMonths(retentionMonths).toInstant();
        List<User> expired = userRepository.findByActiveFalseAndDeactivatedAtBefore(cutoff);
        userRepository.deleteAll(expired);

        verificationTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(request.token())
                .orElseThrow(InvalidOrExpiredTokenException::new);

        if (verificationToken.getType() != TokenType.EMAIL_VERIFICATION || !verificationToken.isValid()) {
            throw new InvalidOrExpiredTokenException();
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsedAt(Instant.now());
        verificationTokenRepository.save(verificationToken);

        // Clicking the link proves email ownership - log them straight in.
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getUsername());
    }

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        // Same response regardless of match/state - don't reveal whether the email is registered.
        userRepository.findByEmail(request.email())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> createAndSendToken(user, TokenType.EMAIL_VERIFICATION));

        return new MessageResponse("If that email is registered and unverified, a confirmation link has been sent.");
    }

    @Transactional
    public MessageResponse requestPasswordReset(PasswordResetRequest request) {
        // Same response regardless of match - don't reveal whether the email is registered.
        userRepository.findByEmail(request.email())
                .ifPresent(user -> createAndSendToken(user, TokenType.PASSWORD_RESET));

        return new MessageResponse("If that email is registered, a password reset link has been sent.");
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(request.token())
                .orElseThrow(InvalidOrExpiredTokenException::new);

        if (verificationToken.getType() != TokenType.PASSWORD_RESET || !verificationToken.isValid()) {
            throw new InvalidOrExpiredTokenException();
        }

        User user = verificationToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        // Invalidate existing tokens - force re-login with the new password.
        user.setCredentialsVersion(user.getCredentialsVersion() + 1);
        userRepository.save(user);

        verificationToken.setUsedAt(Instant.now());
        verificationTokenRepository.save(verificationToken);
    }

    private void createAndSendToken(User user, TokenType type) {
        long expiryMinutes = type == TokenType.EMAIL_VERIFICATION
                ? verificationExpiryHours * 60L
                : passwordResetExpiryMinutes;

        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .type(type)
                .expiresAt(Instant.now().plusSeconds(expiryMinutes * 60))
                .build();
        verificationTokenRepository.save(verificationToken);

        if (type == TokenType.EMAIL_VERIFICATION) {
            mailEventPublisher.publishVerificationEmail(user, verificationToken.getToken());
        } else {
            mailEventPublisher.publishPasswordResetEmail(user, verificationToken.getToken());
        }
    }

    private boolean withinRetentionWindow(Instant deactivatedAt) {
        Instant deadline = deactivatedAt.atZone(ZoneOffset.UTC).plusMonths(retentionMonths).toInstant();
        return Instant.now().isBefore(deadline);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}