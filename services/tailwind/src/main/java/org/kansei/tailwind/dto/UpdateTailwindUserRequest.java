package org.kansei.tailwind.dto;

import org.kansei.tailwind.model.Visibility;

/**
 * Partial update of the caller's own settings, a null field is left as it is.
 */
public record UpdateTailwindUserRequest(Visibility defaultVisibility, Boolean recapEmails) {
}
