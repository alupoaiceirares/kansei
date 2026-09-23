package org.kansei.tailwind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MapAircraftStringRequest(@NotBlank @Size(max = 255) String modelString, @NotNull Long aircraftTypeId) {
}
