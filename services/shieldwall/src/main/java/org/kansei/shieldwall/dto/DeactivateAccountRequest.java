package org.kansei.shieldwall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeactivateAccountRequest(

        @NotBlank(message = "Current password is required")
        @Size(max = 72, message = "Current password must be at most 72 characters")
        String currentPassword
) {
}
