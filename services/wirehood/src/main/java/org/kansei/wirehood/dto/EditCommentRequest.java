package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditCommentRequest(@NotBlank @Size(max = 2000) String body) {
}
