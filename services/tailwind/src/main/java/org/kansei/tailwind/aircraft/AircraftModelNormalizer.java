package org.kansei.tailwind.aircraft;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns free-text model names into comparable keys: lowercase, no manufacturer, no punctuation, no
 * bracketed notes. "Boeing 787-9 (winglets)" and "787-9" both become "7879".
 */
public final class AircraftModelNormalizer {

    private static final Pattern BRACKETED = Pattern.compile("\\([^)]*\\)");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final int MIN_KEY_LENGTH = 2;

    private AircraftModelNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = BRACKETED.matcher(text).replaceAll(" ").toLowerCase(Locale.ROOT);
        return NON_ALPHANUMERIC.matcher(lower).replaceAll("");
    }

    // Removes one leading manufacturer name, longest match first. manufacturers are already lowercase
    public static String stripManufacturer(String text, Collection<String> manufacturers) {
        if (text == null) {
            return "";
        }
        String lower = text.trim().toLowerCase(Locale.ROOT);
        for (String manufacturer : manufacturers.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            if (lower.startsWith(manufacturer)
                    && (lower.length() == manufacturer.length() || !Character.isLetterOrDigit(lower.charAt(manufacturer.length())))) {
                return lower.substring(manufacturer.length()).trim();
            }
        }
        return lower;
    }

    // The designator, the whole model, and every leading-token prefix of the model that contains a digit
    public static Set<String> aliasKeys(String icaoCode, String model) {
        Set<String> keys = new LinkedHashSet<>();
        addKey(keys, normalize(icaoCode));
        StringBuilder prefix = new StringBuilder();
        for (String token : model.trim().split("\\s+")) {
            prefix.append(prefix.isEmpty() ? "" : " ").append(token);
            if (prefix.chars().anyMatch(Character::isDigit)) {
                addKey(keys, normalize(prefix.toString()));
            }
        }
        return keys;
    }

    private static void addKey(Set<String> keys, String key) {
        if (key.length() >= MIN_KEY_LENGTH) {
            keys.add(key);
        }
    }
}
