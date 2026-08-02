package org.kansei.shieldwall.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update - null means "leave this field unchanged".
 * Only apply the constraint when a value is actually provided, since @Email/@Size alone would reject a legitimately absent field otherwise.
 */
public record UpdateProfileRequest(

        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Size(min = 3, max = 40, message = "Username must be between 3 and 40 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Username may only contain letters, numbers, underscores, and hyphens")
        String username,

        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName
) {
}