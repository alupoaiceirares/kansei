package org.kansei.shieldwall.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Fields map directly to the registration JSON body.
 */
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 40, message = "Username must be between 3 and 40 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 10, message = "Password must be at least 10 characters")
        String password,

        String firstName,

        String lastName
) {
}