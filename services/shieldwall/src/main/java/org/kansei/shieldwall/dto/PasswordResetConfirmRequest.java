package org.kansei.shieldwall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(

        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 10, max = 72, message = "New password must be between 10 and 72 characters")
        String newPassword
) {
}
