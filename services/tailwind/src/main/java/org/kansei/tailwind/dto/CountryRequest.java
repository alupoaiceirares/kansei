package org.kansei.tailwind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CountryRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "code must be 2 uppercase letters") String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "continent must be 2 uppercase letters") String continent
) {
}
