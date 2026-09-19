package org.kansei.tailwind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Reference data keyed on the ICAO type designator (A320, B738). family groups variants for stats
 * and is what a family-only model string from the flight API maps to.
 */
@Entity
@Table(name = "aircraft_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AircraftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "icao_code")
    private String icaoCode;

    private String manufacturer;

    private String model;

    private String name;

    private String family;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type")
    private BodyType bodyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine_type")
    private EngineType engineType;

    @Column(name = "engine_count")
    private Short engineCount;

    @Column(name = "wake_category")
    private String wakeCategory;
}
