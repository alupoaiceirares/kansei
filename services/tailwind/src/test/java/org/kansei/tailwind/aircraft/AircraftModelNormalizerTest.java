package org.kansei.tailwind.aircraft;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AircraftModelNormalizerTest {

    private static final List<String> MANUFACTURERS = List.of("airbus", "boeing", "mcdonnell douglas", "de havilland canada", "atr");

    @Test
    void normalizeDropsCaseSpacesPunctuationAndBracketedNotes() {
        assertThat(AircraftModelNormalizer.normalize("787-9 Dreamliner")).isEqualTo("7879dreamliner");
        assertThat(AircraftModelNormalizer.normalize("Boeing 737-800 (winglets)")).isEqualTo("boeing737800");
        assertThat(AircraftModelNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void stripManufacturerRemovesTheLongestLeadingMatch() {
        assertThat(AircraftModelNormalizer.stripManufacturer("Airbus A340", MANUFACTURERS)).isEqualTo("a340");
        assertThat(AircraftModelNormalizer.stripManufacturer("McDonnell Douglas MD-11", MANUFACTURERS)).isEqualTo("md-11");
        assertThat(AircraftModelNormalizer.stripManufacturer("ATR-72-600", MANUFACTURERS)).isEqualTo("-72-600");
        assertThat(AircraftModelNormalizer.stripManufacturer("A320neo", MANUFACTURERS)).isEqualTo("a320neo");
    }

    @Test
    void stripManufacturerNeedsAWordBoundary() {
        // "atr" must not eat the front of an unrelated name
        assertThat(AircraftModelNormalizer.stripManufacturer("Atrium 100", MANUFACTURERS)).isEqualTo("atrium 100");
    }

    @Test
    void aliasKeysCoverDesignatorModelAndDigitBearingPrefixes() {
        assertThat(AircraftModelNormalizer.aliasKeys("B789", "787-9 Dreamliner")).containsExactlyInAnyOrder("b789", "7879", "7879dreamliner");
        assertThat(AircraftModelNormalizer.aliasKeys("DH8D", "DHC-8-400 Dash 8")).contains("dh8d", "dhc8400", "dhc8400dash", "dhc8400dash8");
        assertThat(AircraftModelNormalizer.aliasKeys("A20N", "A320neo")).containsExactlyInAnyOrder("a20n", "a320neo");
    }

    @Test
    void aliasKeysSkipPrefixesWithoutADigitAndTooShortKeys() {
        assertThat(AircraftModelNormalizer.aliasKeys("RJ85", "Avroliner RJ-85")).doesNotContain("avroliner");
        assertThat(AircraftModelNormalizer.aliasKeys("X", "7")).isEmpty();
    }
}
