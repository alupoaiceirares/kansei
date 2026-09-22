// Mirrors the tailwind DTOs. Only the fields the screens actually read are declared.

export type Visibility = 'PUBLIC' | 'FRIENDS' | 'PRIVATE';

export type TailwindUser = {
  userId: string;
  username: string | null;
  joinedAt: string;
  enabled: boolean;
  defaultVisibility: Visibility;
  role: string | null;
};

export type FriendRequest = {
  userId: string;
  username: string | null;
  direction: 'INCOMING' | 'OUTGOING';
  requestedAt: string;
};

export type Friend = {
  userId: string;
  username: string | null;
  friendsSince: string;
};

export type AirportRef = {
  id: number;
  icao: string | null;
  iata: string | null;
  name: string;
  city: string | null;
  countryCode: string;
  timeZone: string | null;
  latitude: number;
  longitude: number;
};

export type AirlineRef = { id: number; icao: string | null; iata: string | null; name: string };

export type AircraftInfo = {
  family: string | null;
  typeIcao: string | null;
  typeName: string | null;
  modelRaw: string | null;
  registration: string | null;
};

export type Flight = {
  id: number;
  flightNumber: string | null;
  flightDate: string;
  source: 'API' | 'MANUAL';
  status: string | null;
  cargo: boolean;
  upcoming: boolean;
  distanceKm: number;
  airline: AirlineRef;
  departureAirport: AirportRef;
  arrivalAirport: AirportRef;
  departureScheduledUtc: string | null;
  departureRevisedUtc: string | null;
  departureActualUtc: string | null;
  arrivalScheduledUtc: string | null;
  arrivalRevisedUtc: string | null;
  arrivalActualUtc: string | null;
  aircraft: AircraftInfo;
};

export type StopType = 'STAY' | 'LAYOVER' | 'LAYOVER_VISITED';
export type SeatPosition = 'WINDOW' | 'MIDDLE' | 'AISLE';
export type CabinClass = 'ECONOMY' | 'PREMIUM_ECONOMY' | 'BUSINESS' | 'FIRST';
export type TripReason = 'LEISURE' | 'BUSINESS';

export type UserFlight = {
  id: number;
  journeyId: number | null;
  flight: Flight;
  visibility: Visibility;
  seat: string | null;
  seatPosition: SeatPosition | null;
  cabinClass: CabinClass | null;
  reason: TripReason | null;
  stopType: StopType | null;
  stopTypeSuggested: StopType | null;
  notes: string | null;
};

export type Journey = {
  id: number;
  title: string;
  titleIsCustom: boolean;
  notes: string | null;
  createdAt: string;
  flights: UserFlight[];
};
