package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProposeGenreRequest(@NotBlank @Size(max = 100) String name) {
}
