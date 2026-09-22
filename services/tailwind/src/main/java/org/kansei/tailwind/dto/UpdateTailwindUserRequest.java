package org.kansei.tailwind.dto;

import jakarta.validation.constraints.NotNull;
import org.kansei.tailwind.model.Visibility;

/**
 * What the user can change about their own tailwind account. The visibility here only applies to flights
 * added from now on, flights already logged keep whatever they were given.
 */
public record UpdateTailwindUserRequest(@NotNull Visibility defaultVisibility) {
}
