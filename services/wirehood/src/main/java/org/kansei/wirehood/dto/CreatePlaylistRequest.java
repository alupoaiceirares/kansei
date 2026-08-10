package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlaylistRequest(@NotBlank @Size(max = 255) String name, boolean shared) {
}
