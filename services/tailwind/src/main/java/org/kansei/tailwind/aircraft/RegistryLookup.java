package org.kansei.tailwind.aircraft;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Registration to ICAO type from the bundled registry snapshot (about 112k airliner and business aircraft).
 * Loaded into memory on first use, never into Postgres, only consulted when a new flight is stored.
 */
@Slf4j
@Component
public class RegistryLookup {

    private static final String RESOURCE = "registry/aircraft-registry.csv.gz";

    private volatile Map<String, String> typeByRegistration;

    public Optional<String> typeFor(String registration) {
        if (registration == null || registration.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry().get(registration.trim().toUpperCase(Locale.ROOT)));
    }

    private Map<String, String> registry() {
        Map<String, String> loaded = typeByRegistration;
        if (loaded == null) {
            synchronized (this) {
                loaded = typeByRegistration;
                if (loaded == null) {
                    loaded = load();
                    typeByRegistration = loaded;
                }
            }
        }
        return loaded;
    }

    private Map<String, String> load() {
        Map<String, String> result = new HashMap<>(200_000);
        Map<String, String> internedTypes = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ClassPathResource(RESOURCE).getInputStream()), StandardCharsets.UTF_8))) {
            reader.readLine(); // header: reg,hex,type
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length >= 3) {
                    result.put(fields[0], internedTypes.computeIfAbsent(fields[2], type -> type));
                }
            }
        } catch (IOException ex) {
            log.warn("Aircraft registry could not be loaded, registration lookups will find nothing: {}", ex.toString());
            return Map.of();
        }
        log.info("Loaded {} aircraft registrations", result.size());
        return result;
    }
}
