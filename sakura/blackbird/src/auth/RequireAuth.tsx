import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { isAuthenticated, rememberReturnTo } from './auth';
import { useSession } from '../session';
import { PageShell } from '../components/PageShell';
import { Spinner } from '../components/Spinner';
import { COLORS } from '../design/tokens';

type Props = {
  children: ReactNode;
  /** The opt-in screen itself must render for a signed-in user who has not joined yet. */
  allowNotOptedIn?: boolean;
};

/**
 * No token sends the user to the arrival screen, which points back at Kansei. A signed-in user
 * who has not joined WTW yet is sent to the opt-in screen instead of a half-empty log.
 */
export function RequireAuth({ children, allowNotOptedIn = false }: Props) {
  const location = useLocation();
  const { me, loading, failed } = useSession();

  if (!isAuthenticated()) {
    rememberReturnTo(location.pathname + location.search);
    return <Navigate to="/arrival" replace />;
  }

  if (loading) {
    return (
      <PageShell nav={false}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading your log
        </div>
      </PageShell>
    );
  }

  if (!me && !failed && !allowNotOptedIn) {
    return <Navigate to="/opt-in" replace />;
  }

  return <>{children}</>;
}
