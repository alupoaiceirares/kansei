import { cargoBox, upcomingPill, VISIBILITY_PILL } from '../design/tokens';
import type { UserFlight, Visibility } from '../api/types';

export function VisibilityPill({ visibility }: { visibility: Visibility }) {
  return <span style={VISIBILITY_PILL[visibility]}>{visibility}</span>;
}

export function CargoMark() {
  return (
    <span style={cargoBox} title="Cargo flight">
      C
    </span>
  );
}

export function UpcomingPill() {
  return <span style={upcomingPill}>UPCOMING</span>;
}

/** The marker set a flight row carries: cargo, upcoming, then its visibility. */
export function FlightFlags({ userFlight }: { userFlight: UserFlight }) {
  return (
    <>
      {userFlight.flight.cargo && <CargoMark />}
      {userFlight.flight.upcoming && <UpcomingPill />}
      <VisibilityPill visibility={userFlight.visibility} />
    </>
  );
}

/** "FRA → JFK" from a flight, falling back to ICAO when an airport has no IATA code. */
export function routeOf(userFlight: UserFlight): string {
  const from = userFlight.flight.departureAirport;
  const to = userFlight.flight.arrivalAirport;
  return (from.iata ?? from.icao ?? '???') + ' → ' + (to.iata ?? to.icao ?? '???');
}

export function aircraftLabel(userFlight: UserFlight): string {
  const aircraft = userFlight.flight.aircraft;
  return aircraft.typeName ?? aircraft.family ?? aircraft.modelRaw ?? 'Unknown aircraft';
}
