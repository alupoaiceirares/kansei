package org.kansei.shieldwall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        // No min - this is a lookup against an existing password, not a new one. Max still
        // caps the cost of the bcrypt comparison against arbitrarily large input.
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password
) {
}