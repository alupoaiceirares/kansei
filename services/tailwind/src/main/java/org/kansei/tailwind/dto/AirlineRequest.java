package org.kansei.tailwind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AirlineRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "icao must be 3 uppercase letters") String icao,
        @Pattern(regexp = "^[A-Z0-9]{2}$", message = "iata must be 2 uppercase letters or digits") String iata,
        @NotBlank @Size(max = 255) String name,
        @Pattern(regexp = "^[A-Z]{2}$", message = "countryCode must be 2 uppercase letters") String countryCode,
        Boolean active
) {
}
