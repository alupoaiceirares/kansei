package org.kansei.shieldwall.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Fields map directly to the registration JSON body.
 */
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 40, message = "Username must be between 3 and 40 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Username may only contain letters, numbers, underscores, and hyphens")
        String username,

        @NotBlank(message = "Password is required")
        // Matches bcrypt's own effective limit
        @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
        String password,

        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName
) {
}
