package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Full replace, both fields required - mirrors EditCommentRequest's single-field replace, just two fields instead of one
public record UpdatePlaylistRequest(@NotBlank @Size(max = 255) String name, boolean shared) {
}
