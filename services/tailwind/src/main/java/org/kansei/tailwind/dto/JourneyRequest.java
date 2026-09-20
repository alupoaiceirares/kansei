package org.kansei.tailwind.dto;

import jakarta.validation.constraints.Size;

/**
 * Create or partial update, a null field is left as it is and a blank title goes back to the automatic one.
 */
public record JourneyRequest(@Size(max = 255) String title, @Size(max = 4000) String notes) {
}
