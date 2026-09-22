import { useSyncExternalStore } from 'react';
import { getToken, getUserId, getUsername, subscribeToken } from './auth';

/** Re-renders whenever the stored token changes (handoff capture, sign out, 401 bounce). */
export function useToken(): string | null {
  return useSyncExternalStore(
    (listener) => subscribeToken(listener),
    getToken,
    () => null,
  );
}

export function useIdentity() {
  const token = useToken();
  return { token, userId: token ? getUserId() : null, username: token ? getUsername() : null };
}
