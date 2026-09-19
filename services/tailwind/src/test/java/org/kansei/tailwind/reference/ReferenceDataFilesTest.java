package org.kansei.tailwind.reference;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundled CSVs the loader imports, a bad row would otherwise only fail at startup on a fresh database.
 */
class ReferenceDataFilesTest {

    private static List<Map<String, String>> read(String file) throws IOException {
        try (InputStream in = new ClassPathResource("db/data/" + file).getInputStream()) {
            return CsvReader.read(in);
        }
    }

    @Test
    void countriesAreUniqueTwoLetterCodes() throws IOException {
        List<Map<String, String>> countries = read("countries.csv");
        Set<String> codes = new HashSet<>();
        for (Map<String, String> c : countries) {
            assertThat(c.get("code")).matches("[A-Z]{2}");
            assertThat(c.get("name")).isNotBlank();
            assertThat(c.get("continent")).matches("[A-Z]{2}");
            assertThat(codes.add(c.get("code"))).as("duplicate country " + c.get("code")).isTrue();
        }
        assertThat(countries.size()).isGreaterThan(200);
    }

    @Test
    void airportsHaveValidUniqueCodesKnownCountriesAndCoordinates() throws IOException {
        Set<String> countries = new HashSet<>();
        read("countries.csv").forEach(c -> countries.add(c.get("code")));
        List<Map<String, String>> airports = read("airports.csv");
        Set<String> iatas = new HashSet<>();
        Set<String> icaos = new HashSet<>();
        for (Map<String, String> a : airports) {
            String icao = a.get("icao");
            String iata = a.get("iata");
            assertThat(!icao.isBlank() || !iata.isBlank()).as("airport without a code: " + a.get("name")).isTrue();
            if (!icao.isBlank()) {
                assertThat(icao).matches("[A-Z]{4}");
                assertThat(icaos.add(icao)).as("duplicate icao " + icao).isTrue();
            }
            if (!iata.isBlank()) {
                assertThat(iata).matches("[A-Z0-9]{3}");
                assertThat(iatas.add(iata)).as("duplicate iata " + iata).isTrue();
            }
            assertThat(a.get("name")).isNotBlank();
            assertThat(countries).contains(a.get("country_code"));
            assertThat(Double.parseDouble(a.get("latitude"))).isBetween(-90.0, 90.0);
            assertThat(Double.parseDouble(a.get("longitude"))).isBetween(-180.0, 180.0);
            if (!a.get("time_zone").isBlank()) {
                assertThat(ZoneId.getAvailableZoneIds()).contains(a.get("time_zone"));
            }
        }
        assertThat(airports.size()).isGreaterThan(8000);
        assertThat(iatas).contains("FRA", "JFK", "OTP", "IST", "LHR");
    }

    @Test
    void airlinesHaveUniqueIcaoAndKnownCountries() throws IOException {
        Set<String> countries = new HashSet<>();
        read("countries.csv").forEach(c -> countries.add(c.get("code")));
        List<Map<String, String>> airlines = read("airlines.csv");
        Set<String> icaos = new HashSet<>();
        for (Map<String, String> a : airlines) {
            assertThat(a.get("icao")).matches("[A-Z]{3}");
            assertThat(icaos.add(a.get("icao"))).as("duplicate icao " + a.get("icao")).isTrue();
            assertThat(a.get("name")).isNotBlank();
            if (!a.get("iata").isBlank()) {
                assertThat(a.get("iata")).matches("[A-Z0-9]{2}");
            }
            if (!a.get("country_code").isBlank()) {
                assertThat(countries).contains(a.get("country_code"));
            }
            assertThat(a.get("active")).isIn("true", "false");
        }
        assertThat(icaos).contains("DLH", "BAW", "RYR", "WZZ", "THY");
    }

    @Test
    void aircraftTypesHaveUniqueDesignatorsAndValidEnums() throws IOException {
        List<Map<String, String>> types = read("aircraft_types.csv");
        Set<String> codes = new HashSet<>();
        for (Map<String, String> t : types) {
            assertThat(t.get("icao_code")).matches("[A-Z0-9]{2,4}");
            assertThat(codes.add(t.get("icao_code"))).as("duplicate designator " + t.get("icao_code")).isTrue();
            assertThat(t.get("manufacturer")).isNotBlank();
            assertThat(t.get("model")).isNotBlank().isEqualTo(t.get("model").trim());
            assertThat(t.get("family")).isNotBlank().isEqualTo(t.get("family").trim());
            assertThat(t.get("body_type")).isIn("NARROW", "WIDE", "REGIONAL", "TURBOPROP", "BUSINESS", "OTHER");
            assertThat(t.get("engine_type")).isIn("JET", "TURBOPROP", "PISTON", "ELECTRIC");
        }
        assertThat(codes).contains("A320", "A20N", "B738", "B38M", "A388", "B744", "AT72", "E190");
    }
}
