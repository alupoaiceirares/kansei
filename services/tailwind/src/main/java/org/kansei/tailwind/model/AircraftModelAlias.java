package org.kansei.tailwind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A normalized model string that resolves to exactly one aircraft type.
 */
@Entity
@Table(name = "aircraft_model_aliases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AircraftModelAlias {

    @Id
    @Column(name = "alias_key")
    private String aliasKey;

    @Column(name = "aircraft_type_id")
    private Long aircraftTypeId;
}
