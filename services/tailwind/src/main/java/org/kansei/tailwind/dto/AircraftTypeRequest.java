package org.kansei.tailwind.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.kansei.tailwind.model.BodyType;
import org.kansei.tailwind.model.EngineType;

/**
 * name defaults to manufacturer + model and family defaults to model when left out.
 */
public record AircraftTypeRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{2,4}$", message = "icaoCode must be 2 to 4 uppercase letters or digits") String icaoCode,
        @NotBlank @Size(max = 255) String manufacturer,
        @NotBlank @Size(max = 255) String model,
        @Size(max = 255) String name,
        @Size(max = 255) String family,
        @NotNull BodyType bodyType,
        @NotNull EngineType engineType,
        @Min(1) @Max(8) Short engineCount,
        @Pattern(regexp = "^[LMHJ]$", message = "wakeCategory must be L, M, H or J") String wakeCategory
) {
}
