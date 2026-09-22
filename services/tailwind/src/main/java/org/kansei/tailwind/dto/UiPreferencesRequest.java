package org.kansei.tailwind.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Display choices the frontend wants remembered per account (map palette, shading, projection, overlays).
 * The backend stores the object as given, it never reads individual keys.
 */
public record UiPreferencesRequest(@NotNull Map<String, Object> preferences) {
}
