import type { Journey, UserFlight, Visibility } from './api/types';
import type { RouteStop } from './map/RouteMap';

/**
 * A journey's flights in flight order. The service returns them ordered, this keeps the screens
 * independent of that.
 */
export function legsOf(journey: Journey): UserFlight[] {
  return [...journey.flights].sort((a, b) => a.flight.flightDate.localeCompare(b.flight.flightDate));
}

export function journeyDistance(journey: Journey): number {
  return legsOf(journey)
    .filter((leg) => !leg.flight.upcoming)
    .reduce((sum, leg) => sum + leg.flight.distanceKm, 0);
}

/** A journey has no visibility of its own, so the card shows the one every leg agrees on. */
export function journeyVisibility(journey: Journey): Visibility | 'MIXED' | null {
  const values = new Set(journey.flights.map((leg) => leg.visibility));
  if (values.size === 0) return null;
  if (values.size > 1) return 'MIXED';
  return [...values][0];
}

/**
 * Stops for the route map. A leg that starts somewhere other than the previous leg's arrival is an
 * unflown gap, drawn dashed and dimmed, which is how a trip that returns from another airport reads.
 */
export function journeyStops(journey: Journey): RouteStop[] {
  const legs = legsOf(journey);
  if (legs.length === 0) return [];

  const stops: RouteStop[] = [];
  legs.forEach((leg, index) => {
    const from = leg.flight.departureAirport;
    const to = leg.flight.arrivalAirport;
    const soon = leg.flight.upcoming;

    if (index === 0) {
      stops.push({
        code: from.iata ?? from.icao ?? '',
        lon: from.longitude,
        lat: from.latitude,
        countryCode: from.countryCode,
        kind: soon ? 'soon' : 'end',
      });
    } else {
      const previous = legs[index - 1].flight.arrivalAirport;
      if (previous.id !== from.id) {
        stops.push({
          code: from.iata ?? from.icao ?? '',
          lon: from.longitude,
          lat: from.latitude,
          countryCode: from.countryCode,
          kind: soon ? 'soon' : 'end',
          gap: true,
        });
      }
    }

    stops.push({
      code: to.iata ?? to.icao ?? '',
      lon: to.longitude,
      lat: to.latitude,
      countryCode: to.countryCode,
      // A layover is a country passed through, not one visited.
      kind: soon ? 'soon' : leg.stopType === 'LAYOVER' ? 'transit' : 'end',
    });
  });
  return stops;
}

export function hasUnflownGap(journey: Journey): boolean {
  return journeyStops(journey).some((stop) => stop.gap);
}

export function routeLine(journey: Journey): string {
  const stops = journeyStops(journey);
  if (stops.length === 0) return '';
  const codes: string[] = [];
  let gapFrom: string | null = null;
  stops.forEach((stop) => {
    if (stop.gap) gapFrom = stop.code;
    else codes.push(stop.code);
  });
  const line = codes.join(' → ');
  return gapFrom ? line + ', home from ' + gapFrom : line;
}

/** Countries on a journey, split into visited and transit-only, in the order they were reached. */
export function journeyCountries(journey: Journey): { code: string; kind: 'visited' | 'transit' }[] {
  const out: { code: string; kind: 'visited' | 'transit' }[] = [];
  for (const stop of journeyStops(journey)) {
    if (!stop.countryCode) continue;
    const kind = stop.kind === 'transit' ? 'transit' : 'visited';
    const existing = out.find((item) => item.code === stop.countryCode);
    if (!existing) out.push({ code: stop.countryCode, kind });
    else if (kind === 'visited') existing.kind = 'visited';
  }
  return out;
}

export function monthLabel(journey: Journey): string {
  const legs = legsOf(journey);
  if (legs.length === 0) return '';
  const [year, month] = legs[0].flight.flightDate.split('-').map(Number);
  return new Date(year, month - 1, 1).toLocaleDateString('en-GB', { month: 'short', year: 'numeric' });
}
