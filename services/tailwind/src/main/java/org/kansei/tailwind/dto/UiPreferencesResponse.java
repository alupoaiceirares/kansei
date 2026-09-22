package org.kansei.tailwind.dto;

import java.util.Map;

/**
 * Empty map when the user has never saved any, so the frontend can apply its own defaults.
 */
public record UiPreferencesResponse(Map<String, Object> preferences) {
}
