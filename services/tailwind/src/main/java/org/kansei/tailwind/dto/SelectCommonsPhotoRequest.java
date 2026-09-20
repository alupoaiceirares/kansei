package org.kansei.tailwind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Only the Commons file title is taken from the client, the URL, author and license are read back from Commons.
 */
public record SelectCommonsPhotoRequest(
        @NotBlank @Size(max = 300) @Pattern(regexp = "^File:.+", message = "title must be a Commons file title starting with File:") String title
) {
}
