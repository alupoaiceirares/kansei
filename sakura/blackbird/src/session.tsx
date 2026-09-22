import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { fetchFriendRequests, fetchMe } from './api/tailwind';
import type { TailwindUser } from './api/types';
import { getEmail, getUsername, isAdmin } from './auth/auth';
import { useToken } from './auth/useAuth';

type SessionValue = {
  /** null once loaded means signed in but not opted in to WTW yet. */
  me: TailwindUser | null;
  loading: boolean;
  failed: boolean;
  isAdmin: boolean;
  displayName: string;
  email: string | null;
  incomingRequests: number;
  refreshMe: () => Promise<void>;
  refreshRequests: () => Promise<void>;
};

const SessionContext = createContext<SessionValue | null>(null);

/** Holds the one /users/me answer and the pending-request count the nav dot needs. */
export function SessionProvider({ children }: { children: ReactNode }) {
  const token = useToken();
  const [me, setMe] = useState<TailwindUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [incomingRequests, setIncomingRequests] = useState(0);

  const refreshMe = useCallback(async () => {
    if (!token) {
      setMe(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      setMe(await fetchMe());
      setFailed(false);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [token]);

  const refreshRequests = useCallback(async () => {
    if (!token) {
      setIncomingRequests(0);
      return;
    }
    try {
      const requests = await fetchFriendRequests();
      setIncomingRequests(requests.filter((request) => request.direction === 'INCOMING').length);
    } catch {
      setIncomingRequests(0);
    }
  }, [token]);

  useEffect(() => {
    void refreshMe();
  }, [refreshMe]);

  useEffect(() => {
    if (me) void refreshRequests();
  }, [me, refreshRequests]);

  const value = useMemo<SessionValue>(
    () => ({
      me,
      loading,
      failed,
      isAdmin: isAdmin(),
      displayName: me?.username ?? getUsername() ?? 'Your account',
      email: getEmail(),
      incomingRequests,
      refreshMe,
      refreshRequests,
    }),
    [me, loading, failed, incomingRequests, refreshMe, refreshRequests],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession used outside SessionProvider');
  return value;
}
