import { api } from './client';
import type {
  AddFlightRequest,
  AircraftTypeOption,
  AirlineOption,
  AirportOption,
  CommonsCandidate,
  Friend,
  FriendRequest,
  Journey,
  LookupEntry,
  ManualFlightRequest,
  PhotoInfo,
  TailwindUser,
  UpdateUserFlightRequest,
  UserFlight,
  UserSearchResult,
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

export function searchUsers(query: string, limit = 12) {
  return api<UserSearchResult[]>('/tailwind/friends/search', { query: { q: query, limit } });
}

export function sendFriendRequest(targetUserId: string) {
  return api<void>('/tailwind/friends/requests/' + targetUserId, { method: 'POST' });
}

export function acceptFriendRequest(requesterId: string) {
  return api<void>('/tailwind/friends/requests/' + requesterId + '/accept', { method: 'POST' });
}

export function declineFriendRequest(requesterId: string) {
  return api<void>('/tailwind/friends/requests/' + requesterId + '/decline', { method: 'POST' });
}

/** Unfriends, or withdraws a request that was sent and never answered. */
export function removeFriend(otherUserId: string) {
  return api<void>('/tailwind/friends/' + otherUserId, { method: 'DELETE' });
}

export function updateDefaultVisibility(defaultVisibility: Visibility) {
  return api<TailwindUser>('/tailwind/users/me', { method: 'PATCH', body: { defaultVisibility } });
}

/** Removes the whole log. The shieldwall account is untouched and opting in again starts from scratch. */
export function deleteOwnLog() {
  return api<void>('/tailwind/users/me', { method: 'DELETE' });
}

/** Admin only. Wikimedia Commons images that could serve as this type's photo. */
export function fetchPhotoCandidates(aircraftTypeId: number, query?: string) {
  return api<CommonsCandidate[]>('/tailwind/admin/aircraft-types/' + aircraftTypeId + '/photo/candidates', {
    query: { q: query },
  });
}

export function selectCommonsPhoto(aircraftTypeId: number, title: string) {
  return api<PhotoInfo>('/tailwind/admin/aircraft-types/' + aircraftTypeId + '/photo/commons', {
    method: 'POST',
    body: { title },
  });
}

export function deleteAircraftPhoto(aircraftTypeId: number) {
  return api<void>('/tailwind/admin/aircraft-types/' + aircraftTypeId + '/photo', { method: 'DELETE' });
}
