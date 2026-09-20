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

import java.time.Instant;
import java.util.UUID;

/**
 * A flight a user took: the personal side (seat, cabin, notes, visibility) on top of the shared canonical flight.
 * stopType describes what happens after this flight and is null until the user sets it, the service suggests one.
 */
@Entity
@Table(name = "user_flights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "journey_id")
    private Long journeyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id")
    private Flight flight;

    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    private String seat;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_position")
    private SeatPosition seatPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "cabin_class")
    private CabinClass cabinClass;

    @Enumerated(EnumType.STRING)
    private TripReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type")
    private StopType stopType;

    private String notes;

    @Column(name = "created_at")
    private Instant createdAt;
}
