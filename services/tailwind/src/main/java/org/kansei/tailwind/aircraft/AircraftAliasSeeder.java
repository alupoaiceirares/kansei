package org.kansei.tailwind.aircraft;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds aircraft_model_aliases from the aircraft types when the table is empty. A key that would point
 * at two different types is dropped, so an alias never guesses.
 */
@Slf4j
@Component
public class AircraftAliasSeeder {

    private final JdbcTemplate jdbcTemplate;

    public AircraftAliasSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void seedIfEmpty() {
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM aircraft_model_aliases", Integer.class);
        if (existing != null && existing > 0) {
            return;
        }
        Map<String, Set<Long>> typesByKey = new HashMap<>();
        jdbcTemplate.query("SELECT id, icao_code, model FROM aircraft_types", rs -> {
            long id = rs.getLong("id");
            for (String key : AircraftModelNormalizer.aliasKeys(rs.getString("icao_code"), rs.getString("model"))) {
                typesByKey.computeIfAbsent(key, k -> new HashSet<>()).add(id);
            }
        });

        List<Object[]> batch = new ArrayList<>();
        typesByKey.forEach((key, ids) -> {
            if (ids.size() == 1) {
                batch.add(new Object[]{key, ids.iterator().next()});
            }
        });
        jdbcTemplate.batchUpdate("INSERT INTO aircraft_model_aliases (alias_key, aircraft_type_id) VALUES (?, ?)", batch);
        log.info("Seeded {} aircraft model aliases ({} ambiguous keys skipped)", batch.size(), typesByKey.size() - batch.size());
    }
}
