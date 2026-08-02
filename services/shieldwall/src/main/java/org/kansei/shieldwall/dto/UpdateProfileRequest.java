package org.kansei.shieldwall.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial update - null means "leave this field unchanged".
 * Only apply the constraint when a value is actually provided, since @Email/@Size alone would reject a legitimately absent field otherwise.
 */
public record UpdateProfileRequest(

        @Email(message = "Email must be a valid email address")
        String email,

        @Size(min = 3, max = 40, message = "Username must be between 3 and 40 characters")
        String username,

        String firstName,

        String lastName
) {
}