package org.kansei.auth.service;

import org.kansei.auth.dto.*;
import org.kansei.auth.exception.EmailAlreadyExistsException;
import org.kansei.auth.exception.InvalidCredentialsException;
import org.kansei.auth.exception.UserNotFoundException;
import org.kansei.auth.exception.UsernameAlreadyExistsException;
import org.kansei.auth.model.User;
import org.kansei.auth.repository.UserRepository;
import org.kansei.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
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