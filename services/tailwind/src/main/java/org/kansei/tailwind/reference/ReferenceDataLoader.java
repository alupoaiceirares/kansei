package org.kansei.tailwind.reference;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.aircraft.AircraftAliasSeeder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Imports the bundled reference data (db/data/*.csv) into any table that is still empty, so a reset
 * database is one restart away from full again. Tables that already have rows, including admin
 * edits, are never touched. Each table loads in its own transaction, all or nothing.
 */
@Slf4j
@Component
public class ReferenceDataLoader implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final AircraftAliasSeeder aircraftAliasSeeder;

    public ReferenceDataLoader(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, AircraftAliasSeeder aircraftAliasSeeder) {
        this.jdbcTemplate = jdbcTemplate;
        this.aircraftAliasSeeder = aircraftAliasSeeder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        // Order matters, airports and airlines reference countries
        loadIfEmpty("countries", "countries.csv",
                "INSERT INTO countries (code, name, continent) VALUES (?, ?, ?)",
                r -> new Object[]{r.get("code"), r.get("name"), r.get("continent")});

        loadIfEmpty("airports", "airports.csv",
                "INSERT INTO airports (icao, iata, name, city, country_code, latitude, longitude, time_zone, airport_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r -> new Object[]{blankToNull(r.get("icao")), blankToNull(r.get("iata")), r.get("name"), blankToNull(r.get("city")),
                        r.get("country_code"), Double.parseDouble(r.get("latitude")), Double.parseDouble(r.get("longitude")),
                        blankToNull(r.get("time_zone")), r.get("airport_type")});

        loadIfEmpty("airlines", "airlines.csv",
                "INSERT INTO airlines (icao, iata, name, country_code, active) VALUES (?, ?, ?, ?, ?)",
                r -> new Object[]{r.get("icao"), blankToNull(r.get("iata")), r.get("name"), blankToNull(r.get("country_code")),
                        Boolean.parseBoolean(r.get("active"))});

        loadIfEmpty("aircraft_types", "aircraft_types.csv",
                "INSERT INTO aircraft_types (icao_code, manufacturer, model, name, family, body_type, engine_type, engine_count, wake_category) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r -> new Object[]{r.get("icao_code"), r.get("manufacturer"), r.get("model"), r.get("name"), r.get("family"),
                        r.get("body_type"), r.get("engine_type"), blankToNull(r.get("engine_count")) == null ? null : Short.parseShort(r.get("engine_count")),
                        blankToNull(r.get("wake_category"))});

        aircraftAliasSeeder.seedIfEmpty();
    }

    private void loadIfEmpty(String table, String file, String insertSql, Function<Map<String, String>, Object[]> mapper) throws IOException {
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        if (existing != null && existing > 0) {
            return;
        }
        List<Map<String, String>> records;
        try (InputStream in = new ClassPathResource("db/data/" + file).getInputStream()) {
            records = CsvReader.read(in);
        }
        List<Object[]> batch = records.stream().map(mapper).toList();
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.batchUpdate(insertSql, batch));
        log.info("Loaded {} rows into {} from {}", batch.size(), table, file);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
