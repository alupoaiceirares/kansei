package org.kansei.tailwind.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * At least one of icao and iata is required, enforced in the service.
 */
public record AirportRequest(
        @Pattern(regexp = "^[A-Z]{4}$", message = "icao must be 4 uppercase letters") String icao,
        @Pattern(regexp = "^[A-Z0-9]{3}$", message = "iata must be 3 uppercase letters or digits") String iata,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String city,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "countryCode must be 2 uppercase letters") String countryCode,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 64) String timeZone,
        @Size(max = 32) String airportType
) {
}
