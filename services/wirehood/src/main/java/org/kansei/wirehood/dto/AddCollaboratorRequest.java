package org.kansei.wirehood.dto;

import java.util.UUID;

// Raw userId, not a username lookup
public record AddCollaboratorRequest(UUID userId) {
}
