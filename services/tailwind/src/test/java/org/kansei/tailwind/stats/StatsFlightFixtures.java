package org.kansei.tailwind.stats;

import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.StopType;
import org.kansei.tailwind.model.TripReason;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Builds StatsFlight rows for the calculator tests without touching a database.
 */
public final class StatsFlightFixtures {

    private static long nextId = 1;

    private StatsFlightFixtures() {
    }

    public record Airport(long id, String iata, String name, String country, double lat, double lon) {
    }

    public static final Airport OTP = new Airport(1, "OTP", "Bucharest Otopeni", "RO", 44.57, 26.10);
    public static final Airport IST = new Airport(2, "IST", "Istanbul", "TR", 41.27, 28.73);
    public static final Airport PEK = new Airport(3, "PEK", "Beijing Capital", "CN", 40.08, 116.58);
    public static final Airport FRA = new Airport(4, "FRA", "Frankfurt", "DE", 50.03, 8.56);
    public static final Airport JFK = new Airport(5, "JFK", "New York JFK", "US", 40.64, -73.78);
    public static final Airport SAW = new Airport(6, "SAW", "Istanbul Sabiha Gokcen", "TR", 40.90, 29.31);

    public static Builder flight(long journeyId, LocalDate date, Airport from, Airport to) {
        return new Builder(journeyId, date, from, to);
    }

    public static final class Builder {
        private final long journeyId;
        private final LocalDate date;
        private final Airport from;
        private final Airport to;
        private double distanceKm = 1000;
        private StopType stopType;
        private StopType suggested;
        private Instant departure;
        private Instant arrival;
        private boolean cargo;
        private long airlineId = 1;
        private String airlineName = "Lufthansa";
        private Long aircraftTypeId = 10L;
        private String aircraftIcao = "A346";
        private String aircraftName = "Airbus A340-600";
        private String family = "A340";
        private CabinClass cabinClass;
        private SeatPosition seatPosition;
        private TripReason reason;

        private Builder(long journeyId, LocalDate date, Airport from, Airport to) {
            this.journeyId = journeyId;
            this.date = date;
            this.from = from;
            this.to = to;
        }

        public Builder distance(double km) {
            this.distanceKm = km;
            return this;
        }

        public Builder stop(StopType stopType) {
            this.stopType = stopType;
            return this;
        }

        public Builder suggestedStop(StopType stopType) {
            this.suggested = stopType;
            return this;
        }

        public Builder times(String departureUtc, String arrivalUtc) {
            this.departure = Instant.parse(departureUtc);
            this.arrival = Instant.parse(arrivalUtc);
            return this;
        }

        public Builder cargo() {
            this.cargo = true;
            return this;
        }

        public Builder airline(long id, String name) {
            this.airlineId = id;
            this.airlineName = name;
            return this;
        }

        public Builder aircraft(Long typeId, String icao, String name, String family) {
            this.aircraftTypeId = typeId;
            this.aircraftIcao = icao;
            this.aircraftName = name;
            this.family = family;
            return this;
        }

        public Builder familyOnly(String family) {
            return aircraft(null, null, null, family);
        }

        public Builder noAircraft() {
            return aircraft(null, null, null, null);
        }

        public Builder cabin(CabinClass cabinClass) {
            this.cabinClass = cabinClass;
            return this;
        }

        public Builder seat(SeatPosition seatPosition) {
            this.seatPosition = seatPosition;
            return this;
        }

        public Builder reason(TripReason reason) {
            this.reason = reason;
            return this;
        }

        public StatsFlight build() {
            long id = nextId++;
            return new StatsFlight(id, journeyId, id, date, "LH" + id, distanceKm, cargo, stopType, suggested, departure, arrival,
                    airlineId, "DLH", "LH", airlineName,
                    from.id(), "X" + from.iata(), from.iata(), from.name(), from.name(), from.country(), from.lat(), from.lon(),
                    to.id(), "X" + to.iata(), to.iata(), to.name(), to.name(), to.country(), to.lat(), to.lon(),
                    aircraftTypeId, aircraftIcao, aircraftName, "Airbus", "WIDE", family, cabinClass, seatPosition, reason);
        }
    }
}
