package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCommentRequest(
        @NotBlank @Size(max = 2000) String body,
        UUID parentCommentId
) {
}
