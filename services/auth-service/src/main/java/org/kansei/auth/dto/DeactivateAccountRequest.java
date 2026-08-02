package org.kansei.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record DeactivateAccountRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword
) {
}
