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
 * Reference data. At least one of icao and iata is set, the flight API identifies airports by either.
 */
@Entity
@Table(name = "airports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Airport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String icao;

    private String iata;

    private String name;

    private String city;

    @Column(name = "country_code")
    private String countryCode;

    private double latitude;

    private double longitude;

    @Column(name = "time_zone")
    private String timeZone;

    @Column(name = "airport_type")
    private String airportType;
}
