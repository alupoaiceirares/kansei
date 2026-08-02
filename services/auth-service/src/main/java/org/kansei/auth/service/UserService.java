package org.kansei.auth.service;

import org.kansei.auth.dto.*;
import org.kansei.auth.exception.EmailAlreadyExistsException;
import org.kansei.auth.exception.InvalidCredentialsException;
import org.kansei.auth.exception.UserNotFoundException;
import org.kansei.auth.exception.UsernameAlreadyExistsException;
import org.kansei.auth.model.User;
import org.kansei.auth.repository.UserRepository;
import org.kansei.auth.security.JwtService;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${account.deactivation.retention-months:3}")
    private int retentionMonths;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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

        String token = jwtService.generateToken(saved);
        return new AuthResponse(token, saved.getId(), saved.getUsername());
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