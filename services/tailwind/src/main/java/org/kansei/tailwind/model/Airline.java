package org.kansei.tailwind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reference data keyed on ICAO, IATA is nullable and not unique because IATA codes get reused.
 */
@Entity
@Table(name = "airlines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Airline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String icao;

    private String iata;

    private String name;

    @Column(name = "country_code")
    private String countryCode;

    @Builder.Default
    private boolean active = true;

    // The reference data has no cargo flag, the name is the only hint. Shared codes default to the passenger carrier
    public boolean looksLikeCargoCarrier() {
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("cargo");
    }
}
