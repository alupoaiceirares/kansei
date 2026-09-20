package org.kansei.tailwind.dto;

import jakarta.validation.constraints.NotNull;
import org.kansei.tailwind.model.Visibility;

public record SetVisibilityRequest(@NotNull Visibility visibility) {
}
