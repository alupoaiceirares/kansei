import { api } from './client';
import type {
  AddFlightRequest,
  AdminUser,
  AircraftTypeOption,
  AircraftTypeRecord,
  AirlineOption,
  AirlineRecord,
  AirportOption,
  AirportRecord,
  CommonsCandidate,
  CountryRecord,
  Friend,
  FriendRequest,
  ImportPreview,
  ImportRun,
  JoinRequest,
  JoinResult,
  Journey,
  LookupEntry,
  MapAircraftStringResult,
  ManualFlightRequest,
  PhotoInfo,
  SharedFriend,
  TailwindUser,
  UnmappedAircraftString,
  UpdateUserFlightRequest,
  UserFlight,
  UserSearchResult,
  Visibility,
  YearlyRecap,
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

export function refreshUserFlight(userFlightId: number) {
  return api<UserFlight>('/tailwind/flights/' + userFlightId + '/refresh', { method: 'POST' });
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

/** Emails an admin asking for the account to be disabled. */
export function requestDisable() {
  return api<void>('/tailwind/users/me/disable-request', { method: 'POST' });
}

/** Admin only. Opted-in users, disabled ones included. */
export function adminSearchUsers(query: string) {
  return api<AdminUser[]>('/tailwind/admin/users/search', { query: { query } });
}

export function disableUser(userId: string) {
  return api<void>('/tailwind/admin/users/' + userId + '/disable', { method: 'POST' });
}

export function fetchUnmappedAircraft() {
  return api<UnmappedAircraftString[]>('/tailwind/admin/aircraft-strings/unmapped');
}

export function mapAircraftString(modelString: string, aircraftTypeId: number) {
  return api<MapAircraftStringResult>('/tailwind/admin/aircraft-strings/mappings', {
    method: 'POST',
    body: { modelString, aircraftTypeId },
  });
}

function importForm(file: File) {
  const form = new FormData();
  form.append('file', file);
  return form;
}

/** Dry run, nothing is written. The commit sends the same file again. */
export function previewImport(targetUserId: string, file: File) {
  return api<ImportPreview>('/tailwind/admin/imports/preview', { method: 'POST', query: { targetUserId }, body: importForm(file) });
}

export function commitImport(targetUserId: string, file: File) {
  return api<ImportRun>('/tailwind/admin/imports', { method: 'POST', query: { targetUserId }, body: importForm(file) });
}

export function fetchImportRuns() {
  return api<ImportRun[]>('/tailwind/admin/imports');
}

export function fetchDisabledUsers() {
  return api<AdminUser[]>('/tailwind/admin/users/disabled');
}

export function enableUser(userId: string) {
  return api<void>('/tailwind/admin/users/' + userId + '/enable', { method: 'POST' });
}

export function fetchCountries() {
  return api<CountryRecord[]>('/tailwind/countries');
}

/** Admin only. id null creates the row, otherwise it is replaced as a whole. */
export function saveAirport(id: number | null, body: Omit<AirportRecord, 'id'>) {
  return api<AirportRecord>('/tailwind/admin/airports' + (id === null ? '' : '/' + id), { method: id === null ? 'POST' : 'PUT', body });
}

export function saveAirline(id: number | null, body: Omit<AirlineRecord, 'id'>) {
  return api<AirlineRecord>('/tailwind/admin/airlines' + (id === null ? '' : '/' + id), { method: id === null ? 'POST' : 'PUT', body });
}

export function saveAircraftType(id: number | null, body: Omit<AircraftTypeRecord, 'id'>) {
  return api<AircraftTypeRecord>('/tailwind/admin/aircraft-types' + (id === null ? '' : '/' + id), {
    method: id === null ? 'POST' : 'PUT',
    body,
  });
}

export function saveCountry(code: string | null, body: CountryRecord) {
  return api<CountryRecord>('/tailwind/admin/countries' + (code === null ? '' : '/' + code), { method: code === null ? 'POST' : 'PUT', body });
}

/** Friends who logged the same flight and let you see it. */
export function fetchSharedWith(userFlightId: number) {
  return api<SharedFriend[]>('/tailwind/flights/' + userFlightId + '/shared');
}

/** "I was on this too" on someone else's entry: added straight away between friends, otherwise a request. */
export function joinFlight(userFlightId: number) {
  return api<JoinResult>('/tailwind/flights/' + userFlightId + '/join', { method: 'POST' });
}

export function fetchJoinRequests() {
  return api<JoinRequest[]>('/tailwind/flights/join-requests');
}

export function acceptJoinRequest(requestId: number) {
  return api<void>('/tailwind/flights/join-requests/' + requestId + '/accept', { method: 'POST' });
}

export function declineJoinRequest(requestId: number) {
  return api<void>('/tailwind/flights/join-requests/' + requestId + '/decline', { method: 'POST' });
}

export function withdrawJoinRequest(requestId: number) {
  return api<void>('/tailwind/flights/join-requests/' + requestId, { method: 'DELETE' });
}

export function fetchRecapYears() {
  return api<number[]>('/tailwind/recaps');
}

export function fetchRecap(year: number) {
  return api<YearlyRecap>('/tailwind/recaps/' + year);
}

export function updateRecapEmails(recapEmails: boolean) {
  return api<TailwindUser>('/tailwind/users/me', { method: 'PATCH', body: { recapEmails } });
}
