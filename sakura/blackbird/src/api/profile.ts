import { graphql } from './client';

// travelProfile resolves its fields lazily, so each screen asks only for what it draws.

export type TravelStats = {
  flightCount: number;
  distanceKm: number;
  timeInAirMinutes: number;
  flightsWithDuration: number;
  countryCount: number;
  airportCount: number;
  airlineCount: number;
  aircraftFamilyCount: number;
  aircraftTypeCount: number;
  longestFlightKm: number | null;
  averageFlightKm: number | null;
  cargoFlightCount: number;
};

export type CountryVisit = {
  code: string;
  name: string;
  continent: string;
  visitCount: number;
  firstVisit: string;
  lastVisit: string;
};

export type AirportVisit = {
  id: string;
  icao: string | null;
  iata: string | null;
  name: string;
  city: string | null;
  countryCode: string;
  latitude: number;
  longitude: number;
  timesUsed: number;
  departures: number;
  arrivals: number;
  firstVisit: string;
  lastVisit: string;
};

export type FlightRecord = {
  userFlightId: string;
  flightNumber: string | null;
  date: string;
  distanceKm: number;
  departureIata: string | null;
  arrivalIata: string | null;
  airlineName: string;
  aircraftName: string | null;
};

export type TravelRecords = {
  longestFlight: FlightRecord | null;
  shortestFlight: FlightRecord | null;
  mostFlownRoute: {
    departureIata: string | null;
    arrivalIata: string | null;
    departureName: string;
    arrivalName: string;
    flightCount: number;
    distanceKm: number;
  } | null;
  busiestMonth: { label: string; count: number; distanceKm: number } | null;
  biggestYear: { label: string; count: number; distanceKm: number } | null;
};

export type DashboardProfile = {
  stats: Pick<TravelStats, 'flightCount' | 'distanceKm' | 'timeInAirMinutes' | 'flightsWithDuration' | 'countryCount' | 'aircraftFamilyCount' | 'airportCount'>;
  countries: { visited: Pick<CountryVisit, 'code' | 'name'>[]; passedThrough: Pick<CountryVisit, 'code' | 'name'>[] };
  aircraft: { family: string; flightCount: number }[];
  records: TravelRecords;
};

const DASHBOARD_QUERY = `
  query Dashboard {
    travelProfile {
      stats {
        flightCount
        distanceKm
        timeInAirMinutes
        flightsWithDuration
        countryCount
        airportCount
        aircraftFamilyCount
      }
      countries {
        visited { code name }
        passedThrough { code name }
      }
      aircraft { family flightCount }
      records {
        longestFlight { departureIata arrivalIata distanceKm }
        shortestFlight { departureIata arrivalIata distanceKm }
        mostFlownRoute { departureIata arrivalIata flightCount }
        busiestMonth { label count }
        biggestYear { label count distanceKm }
      }
    }
  }
`;

export async function fetchDashboardProfile(): Promise<DashboardProfile> {
  const data = await graphql<{ travelProfile: DashboardProfile }>(DASHBOARD_QUERY);
  return data.travelProfile;
}

export type FriendTotals = { userId: string; flightCount: number; distanceKm: number };

/**
 * One query with an alias per friend, so the dashboard's friend list costs a single round trip
 * instead of one per friend.
 */
export async function fetchFriendTotals(userIds: string[]): Promise<FriendTotals[]> {
  if (userIds.length === 0) return [];
  const fields = userIds
    .map((_, index) => `f${index}: travelProfile(userId: $u${index}) { stats { flightCount distanceKm } }`)
    .join('\n    ');
  const params = userIds.map((_, index) => `$u${index}: ID!`).join(', ');
  const query = `query FriendTotals(${params}) {\n    ${fields}\n  }`;
  const variables = Object.fromEntries(userIds.map((id, index) => ['u' + index, id]));
  const data = await graphql<Record<string, { stats: { flightCount: number; distanceKm: number } }>>(query, variables);
  return userIds.map((userId, index) => ({
    userId,
    flightCount: data['f' + index]?.stats.flightCount ?? 0,
    distanceKm: data['f' + index]?.stats.distanceKm ?? 0,
  }));
}
