import { api } from './client';
import type {
  AddFlightRequest,
  AircraftTypeOption,
  AirlineOption,
  AirportOption,
  Friend,
  FriendRequest,
  Journey,
  LookupEntry,
  ManualFlightRequest,
  PhotoInfo,
  TailwindUser,
  UpdateUserFlightRequest,
  UserFlight,
  Visibility,
} from './types';

/** null means the user has not opted in yet, the service answers 404 for that. */
export function fetchMe() {
  return api<TailwindUser | null>('/tailwind/users/me', { allow404: true });
}

export function optIn() {
  return api<TailwindUser>('/tailwind/users/opt-in', { method: 'POST' });
}

export function fetchFriends() {
  return api<Friend[]>('/tailwind/friends');
}

export function fetchFriendRequests() {
  return api<FriendRequest[]>('/tailwind/friends/requests');
}

export function fetchMyFlights() {
  return api<UserFlight[]>('/tailwind/flights/mine');
}

export function fetchUserFlights(ownerId: string) {
  return api<UserFlight[]>('/tailwind/flights/users/' + ownerId);
}

export function fetchJourneys() {
  return api<Journey[]>('/tailwind/journeys');
}

/** A paid provider call, only ever triggered by an explicit search action. */
export function lookupFlight(flightNumber: string, date: string) {
  return api<{ flights: LookupEntry[] }>('/tailwind/flights/lookup', { query: { flightNumber, date } });
}

export function addFlight(request: AddFlightRequest) {
  return api<UserFlight>('/tailwind/flights', { method: 'POST', body: request });
}

export function addManualFlight(request: ManualFlightRequest) {
  return api<UserFlight>('/tailwind/flights/manual', { method: 'POST', body: request });
}

export function updateUserFlight(userFlightId: number, request: UpdateUserFlightRequest) {
  return api<UserFlight>('/tailwind/flights/' + userFlightId, { method: 'PATCH', body: request });
}

export function deleteUserFlight(userFlightId: number) {
  return api<void>('/tailwind/flights/' + userFlightId, { method: 'DELETE' });
}

export function searchAirports(query: string, limit = 8) {
  return api<AirportOption[]>('/tailwind/airports/search', { query: { q: query, limit } });
}

export function searchAirlines(query: string, limit = 8) {
  return api<AirlineOption[]>('/tailwind/airlines/search', { query: { q: query, limit } });
}

export function searchAircraftTypes(query: string, limit = 8) {
  return api<AircraftTypeOption[]>('/tailwind/aircraft-types/search', { query: { q: query, limit } });
}

export function fetchPhotoInfoForType(aircraftTypeId: number) {
  return api<PhotoInfo>('/tailwind/aircraft-types/' + aircraftTypeId + '/photo/info');
}

export function fetchPhotoInfoForFamily(family: string) {
  return api<PhotoInfo>('/tailwind/aircraft-families/photo/info', { query: { name: family } });
}

export function fetchJourney(journeyId: number) {
  return api<Journey>('/tailwind/journeys/' + journeyId);
}

export function createJourney(request: { title?: string | null; notes?: string | null }) {
  return api<Journey>('/tailwind/journeys', { method: 'POST', body: request });
}

export function updateJourney(journeyId: number, request: { title?: string | null; notes?: string | null }) {
  return api<Journey>('/tailwind/journeys/' + journeyId, { method: 'PATCH', body: request });
}

/** The "set all" shortcut: writes the value onto every flight in the journey, not the journey. */
export function setJourneyVisibility(journeyId: number, visibility: Visibility) {
  return api<void>('/tailwind/journeys/' + journeyId + '/visibility', { method: 'POST', body: { visibility } });
}

export function fetchUserJourneys(ownerId: string) {
  return api<Journey[]>('/tailwind/journeys/users/' + ownerId);
}

/** The airline a flight number belongs to, so manual entry can fill it in for the user. */
export function fetchAirlinesForFlightNumber(flightNumber: string) {
  return api<AirlineOption[]>('/tailwind/airlines/for-flight-number', { query: { flightNumber } });
}

export function fetchPreferences() {
  return api<{ preferences: Record<string, unknown> }>('/tailwind/users/me/preferences');
}

export function savePreferences(preferences: Record<string, unknown>) {
  return api<{ preferences: Record<string, unknown> }>('/tailwind/users/me/preferences', {
    method: 'PUT',
    body: { preferences },
  });
}
