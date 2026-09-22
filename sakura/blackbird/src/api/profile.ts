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

export type FriendTotals = { userId: string; flightCount: number; distanceKm: number; countryCount: number };

/**
 * One query with an alias per friend, so the dashboard's friend list costs a single round trip
 * instead of one per friend.
 */
export async function fetchFriendTotals(userIds: string[]): Promise<FriendTotals[]> {
  if (userIds.length === 0) return [];
  const fields = userIds
    .map((_, index) => `f${index}: travelProfile(userId: $u${index}) { stats { flightCount distanceKm countryCount } }`)
    .join('\n    ');
  const params = userIds.map((_, index) => `$u${index}: ID!`).join(', ');
  const query = `query FriendTotals(${params}) {\n    ${fields}\n  }`;
  const variables = Object.fromEntries(userIds.map((id, index) => ['u' + index, id]));
  const data = await graphql<Record<string, { stats: { flightCount: number; distanceKm: number; countryCount: number } }>>(
    query,
    variables,
  );
  return userIds.map((userId, index) => ({
    userId,
    flightCount: data['f' + index]?.stats.flightCount ?? 0,
    distanceKm: data['f' + index]?.stats.distanceKm ?? 0,
    countryCount: data['f' + index]?.stats.countryCount ?? 0,
  }));
}

export type MapProfile = {
  stats: { countryCount: number; airportCount: number };
  countries: {
    visited: { code: string; name: string; visitCount: number }[];
    passedThrough: { code: string; name: string; visitCount: number }[];
  };
  airports: { id: string; iata: string | null; name: string; latitude: number; longitude: number; timesUsed: number }[];
};

const MAP_QUERY = `
  query MapProfile {
    travelProfile {
      stats { countryCount airportCount }
      countries {
        visited { code name visitCount }
        passedThrough { code name visitCount }
      }
      airports { id iata name latitude longitude timesUsed }
    }
  }
`;

export async function fetchMapProfile(): Promise<MapProfile> {
  const data = await graphql<{ travelProfile: MapProfile }>(MAP_QUERY);
  return data.travelProfile;
}

export type StatsProfile = {
  stats: TravelStats;
  countries: { visited: CountryVisit[]; passedThrough: CountryVisit[] };
  airports: AirportVisit[];
  aircraft: {
    family: string;
    flightCount: number;
    distanceKm: number;
    firstFlight: string;
    lastFlight: string;
    variants: { aircraftTypeId: string | null; icaoCode: string | null; name: string; flightCount: number }[];
  }[];
  /** Every airliner family, including the ones never flown, which show as locked. */
  aircraftCatalog: {
    family: string;
    manufacturer: string | null;
    bodyType: string | null;
    variantCount: number;
    flightCount: number;
    photoUrl: string;
  }[];
  airlines: { id: string; name: string; iata: string | null; flightCount: number; distanceKm: number }[];
  breakdowns: {
    cabinClasses: { name: string; count: number }[];
    seatPositions: { name: string; count: number }[];
    reasons: { name: string; count: number }[];
    flightsPerYear: { label: string; count: number; distanceKm: number }[];
    flightsWithCabin: number;
    flightsWithReason: number;
    flightsWithSeatPosition: number;
  };
  records: TravelRecords & {
    firstFlight: FlightRecord | null;
    mostFlownAircraftFamily: { name: string; count: number } | null;
    mostFlownAirline: { name: string; count: number } | null;
    furthestPoint: {
      iata: string | null;
      name: string;
      city: string | null;
      countryCode: string;
      distanceFromHomeKm: number;
      homeIata: string | null;
      date: string;
    } | null;
    longestGap: { days: number; from: string; to: string } | null;
    mostAircraftInAJourney: { journeyId: string; title: string; aircraftCount: number; flightCount: number } | null;
    highestCabin: { cabinClass: string; flightCount: number; firstFlight: string } | null;
  };
};

const STATS_QUERY = `
  query StatsProfile($period: PeriodInput) {
    travelProfile(period: $period) {
      stats {
        flightCount
        distanceKm
        timeInAirMinutes
        flightsWithDuration
        countryCount
        airportCount
        airlineCount
        aircraftFamilyCount
        aircraftTypeCount
        longestFlightKm
        averageFlightKm
        cargoFlightCount
      }
      countries {
        visited { code name continent visitCount firstVisit lastVisit }
        passedThrough { code name continent visitCount firstVisit lastVisit }
      }
      airports { id icao iata name city countryCode latitude longitude timesUsed departures arrivals firstVisit lastVisit }
      aircraft {
        family
        flightCount
        distanceKm
        firstFlight
        lastFlight
        variants { aircraftTypeId icaoCode name flightCount }
      }
      aircraftCatalog { family manufacturer bodyType variantCount flightCount photoUrl }
      airlines { id name iata flightCount distanceKm }
      breakdowns {
        cabinClasses { name count }
        seatPositions { name count }
        reasons { name count }
        flightsPerYear { label count distanceKm }
        flightsWithCabin
        flightsWithReason
        flightsWithSeatPosition
      }
      records {
        longestFlight { userFlightId flightNumber date distanceKm departureIata arrivalIata airlineName aircraftName }
        shortestFlight { userFlightId flightNumber date distanceKm departureIata arrivalIata airlineName aircraftName }
        firstFlight { userFlightId flightNumber date distanceKm departureIata arrivalIata airlineName aircraftName }
        mostFlownRoute { departureIata arrivalIata departureName arrivalName flightCount distanceKm }
        mostFlownAircraftFamily { name count }
        mostFlownAirline { name count }
        busiestMonth { label count distanceKm }
        biggestYear { label count distanceKm }
        furthestPoint { iata name city countryCode distanceFromHomeKm homeIata date }
        longestGap { days from to }
        mostAircraftInAJourney { journeyId title aircraftCount flightCount }
        highestCabin { cabinClass flightCount firstFlight }
      }
    }
  }
`;

export async function fetchStatsProfile(period?: { from?: string; to?: string }): Promise<StatsProfile> {
  const data = await graphql<{ travelProfile: StatsProfile }>(STATS_QUERY, period ? { period } : {});
  return data.travelProfile;
}

export type OtherProfile = {
  stats: Pick<
    TravelStats,
    'flightCount' | 'distanceKm' | 'countryCount' | 'airportCount' | 'aircraftFamilyCount' | 'timeInAirMinutes'
  >;
  countries: { visited: { code: string; name: string }[]; passedThrough: { code: string; name: string }[] };
  aircraft: { family: string; flightCount: number }[];
  records: { firstFlight: { date: string } | null };
};

const OTHER_QUERY = `
  query OtherProfile($userId: ID!) {
    travelProfile(userId: $userId) {
      stats { flightCount distanceKm countryCount airportCount aircraftFamilyCount timeInAirMinutes }
      countries {
        visited { code name }
        passedThrough { code name }
      }
      aircraft { family flightCount }
      records { firstFlight { date } }
    }
  }
`;

/** Someone else's profile, already narrowed by the service to what this viewer may see. */
export async function fetchOtherProfile(userId: string): Promise<OtherProfile> {
  const data = await graphql<{ travelProfile: OtherProfile }>(OTHER_QUERY, { userId });
  return data.travelProfile;
}

/** The same shape for the viewer themselves, so the two can be compared side by side. */
export async function fetchOwnComparableProfile(): Promise<OtherProfile> {
  const data = await graphql<{ travelProfile: OtherProfile }>(OTHER_QUERY.replace('($userId: ID!)', '').replace('(userId: $userId)', ''));
  return data.travelProfile;
}
