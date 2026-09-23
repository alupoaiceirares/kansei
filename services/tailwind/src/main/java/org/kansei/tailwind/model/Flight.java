package org.kansei.tailwind.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Canonical flight shared by every user who took it, so the second user adding it costs no API call.
 * Manual flights are private to the one user_flight that references them.
 */
@Entity
@Table(name = "flights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private FlightSource source;

    @Column(name = "flight_number")
    private String flightNumber;

    @Column(name = "flight_date")
    private LocalDate flightDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airline_id")
    private Airline airline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_airport_id")
    private Airport departureAirport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arrival_airport_id")
    private Airport arrivalAirport;

    private String status;

    @Builder.Default
    @Column(name = "is_cargo")
    private boolean cargo = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;

    @Column(name = "aircraft_family")
    private String aircraftFamily;

    @Column(name = "aircraft_model_raw")
    private String aircraftModelRaw;

    private String registration;

    @Column(name = "mode_s")
    private String modeS;

    @Column(name = "departure_scheduled_utc")
    private Instant departureScheduledUtc;

    @Column(name = "departure_revised_utc")
    private Instant departureRevisedUtc;

    @Column(name = "departure_actual_utc")
    private Instant departureActualUtc;

    @Column(name = "arrival_scheduled_utc")
    private Instant arrivalScheduledUtc;

    @Column(name = "arrival_revised_utc")
    private Instant arrivalRevisedUtc;

    @Column(name = "arrival_actual_utc")
    private Instant arrivalActualUtc;

    @Column(name = "distance_km")
    private double distanceKm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_payload", columnDefinition = "jsonb")
    private String apiPayload;

    @Column(name = "api_last_updated_utc")
    private Instant apiLastUpdatedUtc;

    @Column(name = "created_at")
    private Instant createdAt;

    // Stored from schedule data, cleared once a refresh after landing has pulled the real times and aircraft
    @Builder.Default
    @Column(name = "awaiting_refresh")
    private boolean awaitingRefresh = false;

    // Best known times, actual over revised over scheduled
    public Instant bestDeparture() {
        return departureActualUtc != null ? departureActualUtc : departureRevisedUtc != null ? departureRevisedUtc : departureScheduledUtc;
    }

    public Instant bestArrival() {
        return arrivalActualUtc != null ? arrivalActualUtc : arrivalRevisedUtc != null ? arrivalRevisedUtc : arrivalScheduledUtc;
    }

    // Nobody flew a canceled flight, so it never counts. CanceledUncertain still does until a refresh settles it
    public boolean isCanceled() {
        return "Canceled".equalsIgnoreCase(status);
    }

    // Not landed yet by the best known arrival, without one the flight counts from the day after its date
    public boolean isUpcoming(Instant now) {
        Instant arrival = bestArrival();
        return arrival != null ? arrival.isAfter(now) : flightDate.isAfter(LocalDate.ofInstant(now, ZoneOffset.UTC));
    }
}
