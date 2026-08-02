package org.kansei.auth.controller;

import jakarta.validation.Valid;
import org.kansei.auth.dto.*;
import org.kansei.auth.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        MessageResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        AuthResponse response = userService.verifyEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        MessageResponse response = userService.resendVerification(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        MessageResponse response = userService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        userService.confirmPasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Protected route
     * Scope 1: Proving JwtAuthenticationFilter + SecurityConfig's anyRequest().authenticated() rule are working.
     * Scope 2: Endpoint to get details of user
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        UserResponse response = userService.getCurrentUser(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(Authentication authentication,
                                                      @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        UserResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivates the account. Logging back in within the retention window reactivates it, past the window a scheduled job hard-deletes the account.
     */
    @PostMapping("/me/deactivate")
    public ResponseEntity<Void> deactivateAccount(Authentication authentication,
                                                  @Valid @RequestBody DeactivateAccountRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        userService.deactivateAccount(userId, request);
        return ResponseEntity.noContent().build();
    }
}