package org.kansei.tailwind.aircraft;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryLookupTest {

    private final RegistryLookup registry = new RegistryLookup();

    @Test
    void findsTheVariantBehindAFamilyOnlyModelString() {
        // The flight data said "Airbus A340", the registry knows this tail is the -600
        assertThat(registry.typeFor("D-AIHX")).contains("A346");
    }

    @Test
    void ignoresCaseAndSurroundingSpaces() {
        assertThat(registry.typeFor(" d-aihx ")).contains("A346");
    }

    @Test
    void unknownBlankAndNullRegistrationsFindNothing() {
        assertThat(registry.typeFor("ZZ-NOPE")).isEmpty();
        assertThat(registry.typeFor("")).isEmpty();
        assertThat(registry.typeFor(null)).isEmpty();
    }
}
