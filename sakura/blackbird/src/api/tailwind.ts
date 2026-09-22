import { api } from './client';
import type { Friend, FriendRequest, TailwindUser } from './types';

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
