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
  /** Still on schedule data, a refresh after landing brings the real times and aircraft. */
  awaitingRefresh: boolean;
  /** The provider reported it canceled, it stays in the log but never counts. */
  canceled: boolean;
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

export type AirportOption = {
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

export type AirlineOption = {
  id: number;
  icao: string | null;
  iata: string | null;
  name: string;
  countryCode: string | null;
  active: boolean;
};

export type AircraftTypeOption = {
  id: number;
  icaoCode: string;
  manufacturer: string | null;
  model: string | null;
  name: string;
  family: string | null;
};

export type LookupEntry = { flight: Flight; alreadyInLog: boolean };

export type PhotoInfo = {
  hasPhoto: boolean;
  aircraftTypeId: number | null;
  origin: string | null;
  title: string | null;
  author: string | null;
  license: string | null;
  sourceUrl: string | null;
};

export type AddFlightRequest = {
  flightId: number;
  visibility?: Visibility;
  journeyId?: number | null;
  confirmCargo?: boolean;
  seat?: string | null;
  seatPosition?: SeatPosition | null;
  cabinClass?: CabinClass | null;
  reason?: TripReason | null;
  notes?: string | null;
};

export type ManualFlightRequest = {
  flightNumber?: string | null;
  date: string;
  airlineId: number;
  departureAirportId: number;
  arrivalAirportId: number;
  aircraftTypeId?: number | null;
  /** Local clock times at their own airport, both optional. */
  departureTime?: string | null;
  arrivalTime?: string | null;
  cargo?: boolean;
  visibility?: Visibility;
  journeyId?: number | null;
  seat?: string | null;
  seatPosition?: SeatPosition | null;
  cabinClass?: CabinClass | null;
  reason?: TripReason | null;
  notes?: string | null;
};

export type UpdateUserFlightRequest = {
  visibility?: Visibility;
  journeyId?: number | null;
  seat?: string | null;
  seatPosition?: SeatPosition | null;
  cabinClass?: CabinClass | null;
  reason?: TripReason | null;
  stopType?: StopType | null;
  notes?: string | null;
};

export type UserSearchResult = {
  userId: string;
  username: string | null;
  relation: 'NONE' | 'REQUEST_SENT' | 'REQUEST_RECEIVED' | 'FRIENDS';
};

export type CommonsCandidate = {
  title: string;
  thumbUrl: string;
  pageUrl: string;
  author: string | null;
  license: string | null;
  width: number;
  height: number;
};

/** Admin screens. */
export type AdminUser = {
  userId: string;
  username: string | null;
  enabled: boolean;
  joinedAt: string;
  disabledAt: string | null;
};

export type UnmappedAircraftString = {
  modelString: string;
  family: string | null;
  flightCount: number;
  lastSeen: string;
};

export type MapAircraftStringResult = {
  aliasKey: string;
  aircraftType: { id: number; icaoCode: string; name: string; family: string | null };
  flightsUpdated: number;
};

export type ImportRowStatus = 'READY' | 'DUPLICATE' | 'ERROR';

export type ImportPreview = {
  targetUserId: string;
  targetUsername: string | null;
  totalRows: number;
  readyRows: number;
  duplicateRows: number;
  errorRows: number;
  rows: {
    line: number;
    status: ImportRowStatus;
    message: string | null;
    date: string | null;
    flightNumber: string | null;
    airline: string | null;
    from: string | null;
    to: string | null;
    aircraft: string | null;
    journey: string | null;
  }[];
};

export type ImportRun = {
  id: number;
  adminUserId: string;
  adminUsername: string | null;
  targetUserId: string;
  targetUsername: string | null;
  fileName: string | null;
  totalRows: number;
  importedRows: number;
  duplicateRows: number;
  errorRows: number;
  errors: { line: number; message: string }[];
  createdAt: string;
};
