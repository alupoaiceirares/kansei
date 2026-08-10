package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Raw userId, not a username lookup
public record AddCollaboratorRequest(@NotNull UUID userId) {
}
