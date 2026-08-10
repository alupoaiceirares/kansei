package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record TagTrackRequest(@NotEmpty List<UUID> genreIds) {
}
