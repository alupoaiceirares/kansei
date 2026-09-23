package org.kansei.tailwind.aircraft;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.repository.AircraftModelAliasRepository;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Works out which aircraft a flight used from the API model string and registration. Runs once when a
 * flight is first stored, the result lives on the flight row.
 */
@Slf4j
@Service
public class AircraftResolver {

    public enum Source {ALIAS, REGISTRY, FAMILY, NONE}

    /**
     * type is the exact variant when known, family is filled whenever anything could be resolved.
     */
    public record Resolution(AircraftType type, String family, Source source) {
        static Resolution none() {
            return new Resolution(null, null, Source.NONE);
        }
    }

    private static final String FAMILY_SUFFIX = " family";

    private final AircraftTypeRepository aircraftTypeRepository;
    private final AircraftModelAliasRepository aliasRepository;
    private final RegistryLookup registryLookup;

    public AircraftResolver(AircraftTypeRepository aircraftTypeRepository, AircraftModelAliasRepository aliasRepository,
                            RegistryLookup registryLookup) {
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.aliasRepository = aliasRepository;
        this.registryLookup = registryLookup;
    }

    // The alias table key for a model string, empty when nothing usable is left after normalizing
    public String aliasKey(String modelString) {
        List<String> manufacturers = aircraftTypeRepository.findDistinctManufacturers().stream()
                .map(m -> m.toLowerCase(Locale.ROOT)).toList();
        return AircraftModelNormalizer.normalize(AircraftModelNormalizer.stripManufacturer(modelString, manufacturers));
    }

    public Resolution resolve(String modelString, String registration) {
        String key = aliasKey(modelString);

        AircraftType aliasType = key.isEmpty() ? null : aliasRepository.findById(key)
                .flatMap(alias -> aircraftTypeRepository.findById(alias.getAircraftTypeId())).orElse(null);
        AircraftType registryType = registryLookup.typeFor(registration).flatMap(aircraftTypeRepository::findByIcaoCode).orElse(null);

        // An exact type in the API string wins, a registry hit only refines a family-only or unknown string
        if (aliasType != null) {
            if (registryType != null && !registryType.getId().equals(aliasType.getId())) {
                log.info("Aircraft model '{}' maps to {} but registration {} is {}, keeping the model", modelString,
                        aliasType.getIcaoCode(), registration, registryType.getIcaoCode());
            }
            return new Resolution(aliasType, aliasType.getFamily(), Source.ALIAS);
        }

        String stringFamily = key.isEmpty() ? null : familiesByKey().get(key);
        if (registryType != null && (stringFamily == null || stringFamily.equals(registryType.getFamily()))) {
            return new Resolution(registryType, registryType.getFamily(), Source.REGISTRY);
        }
        if (stringFamily != null) {
            return new Resolution(null, stringFamily, Source.FAMILY);
        }

        log.warn("Unmapped aircraft model string '{}' (registration {}), needs an alias", modelString, registration);
        return Resolution.none();
    }

    private Map<String, String> familiesByKey() {
        Map<String, String> byKey = new HashMap<>();
        for (String family : aircraftTypeRepository.findDistinctFamilies()) {
            byKey.put(AircraftModelNormalizer.normalize(family), family);
            String lower = family.toLowerCase(Locale.ROOT);
            if (lower.endsWith(FAMILY_SUFFIX)) {
                byKey.put(AircraftModelNormalizer.normalize(lower.substring(0, lower.length() - FAMILY_SUFFIX.length())), family);
            }
        }
        return byKey;
    }
}
