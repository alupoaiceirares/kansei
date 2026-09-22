import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PlaneBackdrop } from '../components/PlaneBackdrop';
import { Spinner } from '../components/Spinner';
import { WtwLogo } from '../components/WtwLogo';
import { goToKansei } from '../auth/auth';
import { optIn } from '../api/tailwind';
import { useSession } from '../session';

const POINTS = [
  'Add a flight by number and date, or by hand for anything older than a year',
  'Every flight is one takeoff and one landing; group them into journeys however you like',
  'Countries you only passed through in transit are counted differently from ones you visited',
  'Upcoming flights can be logged early, but stay out of your stats until they happen',
];

const PREVIEW = [
  { label: 'Distance flown', value: '148,392 km', note: '3.7 times around the Earth' },
  { label: 'Countries visited', value: '18', note: 'plus 4 passed through in transit' },
  { label: 'Aircraft families', value: '14', note: 'A340 flown 5 times' },
];

function YesButton({ onClick }: { onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 46,
      padding: '0 26px',
      borderRadius: 9,
      background: COLORS.orange,
      border: 'none',
      color: COLORS.textOnOrange,
      fontFamily: FONT_STACK,
      fontSize: 14.5,
      fontWeight: 700,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <button type="button" onClick={onClick} {...hover}>
      Yes, start my log
    </button>
  );
}

function NoButton({ onClick }: { onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 46,
      padding: '0 22px',
      borderRadius: 9,
      border: '1px solid ' + COLORS.lineStrong,
      background: 'transparent',
      color: COLORS.textMuted,
      fontFamily: FONT_STACK,
      fontSize: 14,
      fontWeight: 500,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { color: COLORS.text, borderColor: COLORS.cyan },
  );
  return (
    <button type="button" onClick={onClick} {...hover}>
      No thanks
    </button>
  );
}

function BackToKanseiButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 44,
      padding: '0 22px',
      borderRadius: 8,
      background: COLORS.orange,
      border: 'none',
      color: COLORS.textOnOrange,
      fontFamily: FONT_STACK,
      fontSize: 14,
      fontWeight: 700,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <button type="button" onClick={goToKansei} {...hover}>
      Back to Kansei
    </button>
  );
}

function TellMeMoreButton({ onClick }: { onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 44,
      padding: '0 22px',
      borderRadius: 8,
      border: '1px solid ' + COLORS.lineStrong,
      background: 'transparent',
      color: COLORS.textMuted,
      fontFamily: FONT_STACK,
      fontSize: 14,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { color: COLORS.text, borderColor: COLORS.cyan },
  );
  return (
    <button type="button" onClick={onClick} {...hover}>
      Actually, tell me more
    </button>
  );
}

type View = 'invite' | 'creating' | 'declined';

/** The join gate. Nothing exists server-side for this user until the opt-in call succeeds. */
export function OptInPage() {
  const navigate = useNavigate();
  const { refreshMe } = useSession();
  const [view, setView] = useState<View>('invite');
  const [error, setError] = useState<string | null>(null);

  const join = async () => {
    setView('creating');
    setError(null);
    try {
      await optIn();
      await refreshMe();
      navigate('/dashboard', { replace: true });
    } catch {
      setError('We could not set up your log. Try again in a moment.');
      setView('invite');
    }
  };

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
          padding: '64px 32px',
        }}
      >
        {view === 'invite' && (
          <div
            style={{
              width: '100%',
              maxWidth: 940,
              display: 'grid',
              gridTemplateColumns: 'minmax(0,1.05fr) minmax(0,0.95fr)',
              background: COLORS.surface,
              border: '1px solid ' + COLORS.lineStrong,
              borderRadius: 18,
              overflow: 'hidden',
              boxShadow: '0 30px 70px rgba(0,0,0,0.5)',
            }}
          >
            <div style={{ padding: '48px 44px', display: 'flex', flexDirection: 'column', gap: 26 }}>
              <WtwLogo width={132} height={73} title="WTW" />

              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                <h1 style={{ margin: 0, fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em', lineHeight: 1.12 }}>
                  Would you like to start tracking your flights?
                </h1>
                <p style={{ margin: 0, fontSize: 15, lineHeight: 1.7, color: COLORS.textMuted, textWrap: 'pretty' }}>
                  World Travel Watch keeps a log of every flight you have taken. Add a flight by its number and date, or enter it
                  by hand, and it turns into a map, a set of stats, aircraft you have collected and records you can compare with
                  friends.
                </p>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
                {POINTS.map((point) => (
                  <div key={point} style={{ display: 'flex', alignItems: 'flex-start', gap: 11 }}>
                    <svg viewBox="0 0 16 16" width={15} height={15} style={{ marginTop: 3, flexShrink: 0 }} aria-hidden="true">
                      <path d="M3 8.5 L6.5 12 L13 4.5" fill="none" stroke={COLORS.cyan} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                    <span style={{ fontSize: 13.5, lineHeight: 1.55, color: COLORS.bodyOnCard }}>{point}</span>
                  </div>
                ))}
              </div>

              {error && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 12,
                    padding: '13px 16px',
                    borderRadius: 10,
                    background: COLORS.dangerBg,
                    border: '1px solid #6B2830',
                    fontSize: 13,
                    color: COLORS.dangerText,
                  }}
                >
                  {error}
                </div>
              )}

              <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginTop: 4 }}>
                <YesButton onClick={join} />
                <NoButton onClick={() => setView('declined')} />
              </div>

              <div style={{ fontSize: 12.5, color: COLORS.textDim, lineHeight: 1.6 }}>
                New flights are visible to your friends by default. You can change that per flight, or set a different default
                later.
              </div>
            </div>

            <div
              style={{
                position: 'relative',
                background: '#082834',
                borderLeft: '1px solid ' + COLORS.line,
                padding: '40px 36px',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                gap: 18,
                overflow: 'hidden',
              }}
            >
              <svg
                style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.55 }}
                viewBox="0 0 420 520"
                preserveAspectRatio="xMidYMid slice"
                aria-hidden="true"
              >
                <g fill="none" stroke="#0F3D4C" strokeWidth={1}>
                  <path d="M-40 420 C100 250 300 200 470 300" />
                  <path d="M-40 300 C120 140 320 120 470 210" />
                  <path d="M-40 500 C140 380 320 340 470 400" />
                </g>
              </svg>
              <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: 14 }}>
                {PREVIEW.map((item) => (
                  <div
                    key={item.label}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 5,
                      padding: '16px 18px',
                      background: COLORS.surface,
                      border: '1px solid ' + COLORS.line,
                      borderRadius: 11,
                    }}
                  >
                    <span style={{ fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim }}>
                      {item.label}
                    </span>
                    <span style={{ fontSize: 25, fontWeight: 600, color: COLORS.text, fontVariantNumeric: 'tabular-nums', lineHeight: 1.1 }}>
                      {item.value}
                    </span>
                    <span style={{ fontSize: 12, color: COLORS.textDim }}>{item.note}</span>
                  </div>
                ))}
                <div style={{ fontSize: 11, color: COLORS.textFaint, textAlign: 'center', letterSpacing: '0.06em' }}>Example figures</div>
              </div>
            </div>
          </div>
        )}

        {view === 'creating' && (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 18, textAlign: 'center' }}>
            <Spinner size={22} />
            <div style={{ fontSize: 17, fontWeight: 600 }}>Setting up your log</div>
            <div style={{ fontSize: 13.5, color: COLORS.textMuted }}>This only takes a moment.</div>
          </div>
        )}

        {view === 'declined' && (
          <div
            style={{
              width: '100%',
              maxWidth: 440,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 20,
              textAlign: 'center',
            }}
          >
            <WtwLogo width={150} height={83} eyeStroke={COLORS.lineStrong} wStroke={COLORS.lineStrong} irisColor="#7A4A2A" title="WTW" />
            <h1 style={{ margin: 0, fontSize: 25, fontWeight: 600, letterSpacing: '-0.01em' }}>No log for now</h1>
            <p style={{ margin: 0, fontSize: 15, lineHeight: 1.65, color: COLORS.textMuted, textWrap: 'pretty' }}>
              Nothing was created. You can come back to WTW from your Kansei account whenever you change your mind.
            </p>
            <div style={{ display: 'flex', gap: 11, flexWrap: 'wrap', justifyContent: 'center' }}>
              <BackToKanseiButton />
              <TellMeMoreButton onClick={() => setView('invite')} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
