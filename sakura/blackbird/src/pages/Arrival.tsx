import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { COLORS, FONT_STACK, primaryButton, primaryButtonHover } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PlaneBackdrop } from '../components/PlaneBackdrop';
import { Spinner } from '../components/Spinner';
import { isAuthenticated, peekReturnTo, redirectToLogin, takeReturnTo } from '../auth/auth';
import { useSession } from '../session';

const EYE = 'M6 66 C50 20 190 20 234 66 C190 112 50 112 6 66 Z';
const W_LEFT = 'M7 66 L46 66 L56 79 L66 55 L76 79 L86 66 L104 66';
const W_RIGHT = 'M136 66 L154 66 L164 79 L174 55 L184 79 L194 66 L233 66';
const IRIS =
  'M99 66 A21 21 0 1 0 141 66 A21 21 0 1 0 99 66 Z M120 49.3 L121.7 55.8 L132.6 64.6 L132.6 67.4 L121.7 63.6 L121.7 74.2 L125.8 78.1 L125.8 79.8 L120.9 77.1 L120.4 81.5 L119.6 81.5 L119.1 77.1 L114.2 79.8 L114.2 78.1 L118.3 74.2 L118.3 63.6 L107.4 67.4 L107.4 64.6 L118.3 55.8 Z';

const LABELS: Record<string, string> = {
  '/dashboard': 'Home',
  '/map': 'Map',
  '/flights': 'My flights',
  '/journeys': 'Journeys',
  '/stats': 'Stats',
  '/friends': 'Friends',
  '/profile': 'My profile',
  '/add': 'Add flight',
};

function labelFor(path: string): string {
  const base = '/' + path.split('/').filter(Boolean)[0];
  return LABELS[base] ?? 'your log';
}

function SignInButton({ label }: { label: string }) {
  const hover = useHoverStyle({ ...primaryButton, height: 44, padding: '0 24px' }, primaryButtonHover);
  return (
    <button type="button" onClick={redirectToLogin} {...hover}>
      {label}
    </button>
  );
}

/**
 * The handoff moment from Kansei. The token arrives in the URL fragment, is captured on load and
 * stripped from the address bar, so this screen only reports what happened.
 */
export function ArrivalPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { me, loading, failed } = useSession();
  const reason = params.get('reason');
  const signedIn = isAuthenticated();
  const state = !signedIn ? (reason === 'expired' ? 'expired' : 'no-token') : 'verifying';

  useEffect(() => {
    if (!signedIn || loading || failed) return;
    // Opted in already goes where they were headed, otherwise the join screen comes first.
    const target = me ? (takeReturnTo() ?? '/dashboard') : '/opt-in';
    navigate(target, { replace: true });
  }, [signedIn, loading, failed, me, navigate]);

  const dim = state !== 'verifying';
  const traceStyle = dim
    ? { stroke: COLORS.lineStrong, strokeDashoffset: 0 }
    : { animation: 'bbTrace 2.2s ease-in-out infinite' };
  const traceStyle2 = dim
    ? { stroke: COLORS.lineStrong, strokeDashoffset: 0 }
    : { animation: 'bbTrace 2.2s ease-in-out 0.35s infinite' };

  return (
    <div
      style={{
        position: 'relative',
        minHeight: '100vh',
        background: COLORS.ground,
        fontFamily: FONT_STACK,
        color: COLORS.text,
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <PlaneBackdrop />

      <div
        style={{
          position: 'relative',
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '72px 32px',
        }}
      >
        <div
          style={{
            width: '100%',
            maxWidth: 460,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 30,
            textAlign: 'center',
          }}
        >
          <svg viewBox="0 0 240 132" width={200} height={110} role="img" aria-label="WTW">
            <path d={EYE} fill="none" stroke={COLORS.lineStrong} strokeWidth={4.2} />
            <path
              d={W_LEFT}
              fill="none"
              stroke={COLORS.cyan}
              strokeWidth={5.2}
              strokeLinejoin="miter"
              strokeMiterlimit={12}
              strokeDasharray="142 142"
              style={traceStyle}
            />
            <path
              d={W_RIGHT}
              fill="none"
              stroke={COLORS.cyan}
              strokeWidth={5.2}
              strokeLinejoin="miter"
              strokeMiterlimit={12}
              strokeDasharray="142 142"
              style={traceStyle2}
            />
            <path
              d={IRIS}
              fill={dim ? '#7A4A2A' : COLORS.orange}
              fillRule="evenodd"
              transform="translate(120 66) scale(0.85) translate(-120 -66)"
            />
          </svg>

          {state === 'verifying' && (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 11, fontSize: 15, color: COLORS.textMuted }}>
                <Spinner size={15} />
                Signing you in
              </div>
              <div style={{ fontSize: 13, color: COLORS.textFaint }}>
                {failed ? 'Waiting for WTW to answer' : 'Reading your session from Kansei'}
              </div>
            </div>
          )}

          {state === 'no-token' && (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 18 }}>
              <div
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '5px 12px',
                  borderRadius: 999,
                  background: COLORS.dangerBg,
                  border: '1px solid #6B2830',
                  color: '#FF8F8F',
                  fontSize: 11,
                  fontWeight: 600,
                  letterSpacing: '0.1em',
                }}
              >
                NO VALID SESSION
              </div>
              <h1 style={{ margin: 0, fontSize: 27, fontWeight: 600, letterSpacing: '-0.01em' }}>We could not sign you in</h1>
              <p
                style={{
                  margin: 0,
                  fontSize: 15,
                  lineHeight: 1.65,
                  color: COLORS.textMuted,
                  maxWidth: 380,
                  textWrap: 'pretty',
                }}
              >
                WTW is opened from your Kansei account. The link you used had no session attached, or it had already been used.
              </p>
              <SignInButton label="Go to Kansei login" />
            </div>
          )}

          {state === 'expired' && (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 18 }}>
              <div
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '5px 12px',
                  borderRadius: 999,
                  background: '#2E250C',
                  border: '1px solid #6B5A20',
                  color: COLORS.warning,
                  fontSize: 11,
                  fontWeight: 600,
                  letterSpacing: '0.1em',
                }}
              >
                SESSION EXPIRED
              </div>
              <h1 style={{ margin: 0, fontSize: 27, fontWeight: 600, letterSpacing: '-0.01em' }}>You have been signed out</h1>
              <p
                style={{
                  margin: 0,
                  fontSize: 15,
                  lineHeight: 1.65,
                  color: COLORS.textMuted,
                  maxWidth: 380,
                  textWrap: 'pretty',
                }}
              >
                Your session ended, either because it expired or because you logged out somewhere else. Sign in again and we will
                put you back where you were.
              </p>
              {peekReturnTo() && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 9,
                    padding: '9px 14px',
                    borderRadius: 8,
                    background: COLORS.surface,
                    border: '1px solid ' + COLORS.line,
                    fontSize: 12.5,
                    color: COLORS.textMuted,
                  }}
                >
                  <span style={{ color: COLORS.textDim }}>Returning to</span>
                  <span style={{ color: '#7FCFE8', fontWeight: 500 }}>{labelFor(peekReturnTo() ?? '')}</span>
                </div>
              )}
              <SignInButton label="Sign in again" />
            </div>
          )}

          <div style={{ fontSize: 12, color: COLORS.textFaint, letterSpacing: '0.04em' }}>
            {state === 'verifying'
              ? 'The token arrives in the URL fragment and is stripped from the address bar before the app continues.'
              : 'blackbird never shows a login form of its own.'}
          </div>
        </div>
      </div>
    </div>
  );
}
