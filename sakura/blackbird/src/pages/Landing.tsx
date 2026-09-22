import { Link } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PlaneBackdrop } from '../components/PlaneBackdrop';
import { Footer } from '../components/Footer';
import { WtwLogoHover } from '../components/WtwLogo';
import { RouteMap } from '../map/RouteMap';
import { signOut } from '../auth/auth';

// The public pitch. No search field here: a flight cannot be logged without an account.
const IDEAS = [
  {
    tag: 'The unit',
    title: 'One flight is one takeoff and one landing',
    body: 'Bucharest to Beijing via Istanbul is two flights in one journey. How you group them is entirely your call, and a journey can fly home from a different airport than it arrived at.',
  },
  {
    tag: 'Privacy',
    title: 'Friends by default, never public by accident',
    body: 'Visibility is set per flight: public, friends, or private. Nothing is shared until you choose it, and private flights still count in your own numbers.',
  },
  {
    tag: 'Aircraft',
    title: 'Family first, variant underneath',
    body: 'A card reads A340, five times. Open it and you get the variants — a 600 three times, a 300 once, and one the data could not pin down. Both answers come from the same log.',
  },
];

// Real Ireland and Romania outlines, the same component the journey maps use.
const PREVIEW_STOPS = [
  { code: 'DUB', lon: -6.27, lat: 53.42, countryCode: 'IE', kind: 'end' as const },
  { code: 'OTP', lon: 26.1, lat: 44.57, countryCode: 'RO', kind: 'end' as const },
];

function JoinButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 38,
      padding: '0 18px',
      borderRadius: 999,
      background: COLORS.orange,
      color: COLORS.textOnOrange,
      fontSize: 13.5,
      fontWeight: 700,
      textDecoration: 'none',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <Link to="/opt-in" {...hover}>
      Join WTW
    </Link>
  );
}

function SignOutPill() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 38,
      padding: '0 18px',
      borderRadius: 999,
      border: '1px solid ' + COLORS.lineStrong,
      background: 'transparent',
      color: COLORS.text,
      fontFamily: FONT_STACK,
      fontSize: 13.5,
      fontWeight: 500,
      whiteSpace: 'nowrap',
      cursor: 'pointer',
    },
    { borderColor: COLORS.cyan },
  );
  return (
    <button type="button" onClick={signOut} {...hover}>
      Sign out
    </button>
  );
}

function StartButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 10,
      height: 54,
      padding: '0 28px',
      borderRadius: 10,
      background: COLORS.orange,
      color: COLORS.textOnOrange,
      fontSize: 15.5,
      fontWeight: 700,
      textDecoration: 'none',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <Link to="/opt-in" {...hover}>
      Start my log
      <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={COLORS.textOnOrange} strokeWidth={2.4} strokeLinecap="round" aria-hidden="true">
        <path d="M5 12h14M14 7l5 5-5 5" />
      </svg>
    </Link>
  );
}

function HeaderLink({ to, children, hash }: { to?: string; hash?: string; children: string }) {
  const hover = useHoverStyle({ fontSize: 13.5, color: COLORS.textMuted, textDecoration: 'none' }, { color: COLORS.cyanBright });
  if (hash) {
    return (
      <a href={hash} {...hover}>
        {children}
      </a>
    );
  }
  return (
    <Link to={to ?? '/'} {...hover}>
      {children}
    </Link>
  );
}

const featureCard = {
  display: 'flex',
  flexDirection: 'column' as const,
  gap: 14,
  padding: 22,
  background: 'rgba(8,40,52,0.8)',
  border: '1px solid ' + COLORS.line,
  borderRadius: 13,
};

export function LandingPage() {
  return (
    <div
      style={{
        position: 'relative',
        minHeight: '100vh',
        background: COLORS.ground,
        fontFamily: FONT_STACK,
        color: COLORS.text,
        overflowX: 'hidden',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <PlaneBackdrop />

      <header
        style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 20,
          padding: '22px 36px',
          maxWidth: 1240,
          margin: '0 auto',
          width: '100%',
          boxSizing: 'border-box',
        }}
      >
        <WtwLogoHover width={168} height={92} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 22 }}>
          <HeaderLink hash="#what">What it does</HeaderLink>
          <HeaderLink to="/dashboard">My log</HeaderLink>
          <JoinButton />
          <SignOutPill />
        </div>
      </header>

      <section
        style={{
          position: 'relative',
          maxWidth: 1240,
          margin: '0 auto',
          width: '100%',
          boxSizing: 'border-box',
          padding: '66px 36px 88px',
          display: 'grid',
          gridTemplateColumns: 'minmax(0,1fr)',
          gap: 40,
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 700 }}>
          <div style={{ fontSize: 11, letterSpacing: '0.22em', textTransform: 'uppercase', color: COLORS.orange }}>World Travel Watch</div>
          <h1
            style={{
              margin: 0,
              fontSize: 'clamp(38px, 5.6vw, 62px)',
              fontWeight: 600,
              letterSpacing: '-0.03em',
              lineHeight: 1.04,
              textWrap: 'balance',
            }}
          >
            A log of every flight you have taken.
          </h1>
          <p style={{ margin: 0, fontSize: 17, lineHeight: 1.65, color: COLORS.textMuted, maxWidth: 560, textWrap: 'pretty' }}>
            Add a flight by its number and date. WTW turns it into a map of where you have been, the distance you have covered, the
            aircraft you have flown and the records you can hold over your friends.
          </p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <StartButton />
          </div>
          <div style={{ fontSize: 12.5, lineHeight: 1.6, color: COLORS.textDim, maxWidth: 520 }}>
            Signed in with your Kansei account. Joining WTW takes one tap and nothing is shared with your friends until you say so.
          </div>
        </div>
      </section>

      <section
        id="what"
        style={{
          position: 'relative',
          maxWidth: 1240,
          margin: '0 auto',
          width: '100%',
          boxSizing: 'border-box',
          padding: '0 36px 88px',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
          gap: 22,
        }}
      >
        {IDEAS.map((idea) => (
          <div
            key={idea.tag}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 11,
              padding: '26px 24px',
              background: 'rgba(10,44,57,0.78)',
              border: '1px solid ' + COLORS.line,
              borderRadius: 14,
            }}
          >
            <div style={{ fontSize: 11, letterSpacing: '0.16em', textTransform: 'uppercase', color: COLORS.orange }}>{idea.tag}</div>
            <div style={{ fontSize: 18, fontWeight: 600, letterSpacing: '-0.01em', lineHeight: 1.25 }}>{idea.title}</div>
            <div style={{ fontSize: 14, lineHeight: 1.65, color: COLORS.textMuted, textWrap: 'pretty' }}>{idea.body}</div>
          </div>
        ))}
      </section>

      <section
        style={{
          position: 'relative',
          maxWidth: 1240,
          margin: '0 auto',
          width: '100%',
          boxSizing: 'border-box',
          padding: '0 36px 96px',
          display: 'flex',
          flexDirection: 'column',
          gap: 26,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 14, flexWrap: 'wrap' }}>
          <h2 style={{ margin: 0, fontSize: 26, fontWeight: 600, letterSpacing: '-0.02em' }}>What a log turns into</h2>
          <span style={{ fontSize: 13.5, color: COLORS.textDim }}>All of it fills in as you add flights. Nothing here is guessed.</span>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(230px, 1fr))', gap: 18 }}>
          <div style={featureCard}>
            <div
              style={{
                position: 'relative',
                height: 128,
                margin: '-4px -6px 0',
                borderRadius: 10,
                background: '#071D28',
                border: '1px solid ' + COLORS.lineSoft,
                overflow: 'hidden',
              }}
            >
              <RouteMap stops={PREVIEW_STOPS} compact />
            </div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>A map</div>
            <div style={{ fontSize: 13.5, lineHeight: 1.6, color: COLORS.textMuted }}>
              Countries coloured by how often you have been, with the ones you only transited through marked differently.
            </div>
          </div>

          <div style={featureCard}>
            <div style={{ height: 56, display: 'flex', alignItems: 'center' }}>
              <svg viewBox="0 0 120 56" width={120} height={56} aria-hidden="true">
                <g fill={COLORS.lineStrong}>
                  <rect x={6} y={34} width={13} height={18} rx={2} />
                  <rect x={25} y={24} width={13} height={28} rx={2} />
                  <rect x={44} y={30} width={13} height={22} rx={2} />
                  <rect x={63} y={12} width={13} height={40} rx={2} fill={COLORS.cyan} />
                  <rect x={82} y={26} width={13} height={26} rx={2} />
                  <rect x={101} y={38} width={13} height={14} rx={2} />
                </g>
              </svg>
            </div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>Stats</div>
            <div style={{ fontSize: 13.5, lineHeight: 1.6, color: COLORS.textMuted }}>
              Distance, time in air, countries, airports, airlines, longest flight — over all time or any period you pick.
            </div>
          </div>

          <div style={featureCard}>
            <div style={{ height: 56, display: 'flex', alignItems: 'center' }}>
              <svg viewBox="0 0 132 56" width={132} height={56} aria-hidden="true">
                <path
                  transform="translate(3 9) scale(0.79)"
                  fill={COLORS.cyan}
                  d="M24 3 C25.7 3 26.9 5.2 26.9 8.4 L26.9 20 L44.5 31.4 L44.5 34.6 L26.9 28.9 L26.9 38.4 L31.3 42.4 L31.3 44.6 L24 42 L16.7 44.6 L16.7 42.4 L21.1 38.4 L21.1 28.9 L3.5 34.6 L3.5 31.4 L21.1 20 L21.1 8.4 C21.1 5.2 22.3 3 24 3 Z"
                />
                <path
                  transform="translate(47 9) scale(0.79)"
                  fill={COLORS.lineStrong}
                  d="M24 7 C25.4 7 26.4 9 26.4 11.8 L26.4 22.4 L39.6 28.8 L39.6 31.4 L26.4 27.6 L26.4 36.8 L29.9 40.8 L29.9 42.8 L24 40.8 L18.1 42.8 L18.1 40.8 L21.6 36.8 L21.6 27.6 L8.4 31.4 L8.4 28.8 L21.6 22.4 L21.6 11.8 C21.6 9 22.6 7 24 7 Z"
                />
                <path
                  transform="translate(91 9) scale(0.79)"
                  fill={COLORS.lineStrong}
                  opacity={0.45}
                  d="M24 7.5 C25.3 7.5 26.2 9.4 26.2 12.2 L26.2 19.6 L43 19.6 L43 23.8 L26.2 23.8 L26.2 37.6 L31 41.2 L31 43.2 L24 41.2 L17 43.2 L17 41.2 L21.8 37.6 L21.8 23.8 L5 23.8 L5 19.6 L21.8 19.6 L21.8 12.2 C21.8 9.4 22.7 7.5 24 7.5 Z"
                />
              </svg>
            </div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>Aircraft collected</div>
            <div style={{ fontSize: 13.5, lineHeight: 1.6, color: COLORS.textMuted }}>
              One card per family you have flown, with a photo. The ones you have never been on stay as locked silhouettes.
            </div>
          </div>

          <div style={featureCard}>
            <div style={{ height: 56, display: 'flex', alignItems: 'center' }}>
              <svg viewBox="0 0 120 56" width={120} height={56} aria-hidden="true">
                <path d="M20 46 H100" fill="none" stroke="#123E4D" strokeWidth={1.6} />
                <rect x={24} y={28} width={20} height={18} rx={2} fill="none" stroke={COLORS.lineStrong} strokeWidth={1.6} />
                <rect x={50} y={14} width={20} height={32} rx={2} fill="none" stroke={COLORS.orange} strokeWidth={1.6} />
                <rect x={76} y={34} width={20} height={12} rx={2} fill="none" stroke={COLORS.lineStrong} strokeWidth={1.6} />
              </svg>
            </div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>Records</div>
            <div style={{ fontSize: 13.5, lineHeight: 1.6, color: COLORS.textMuted }}>
              Longest and shortest flight, most flown route, busiest month, biggest year — and your friends' versions of the same.
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
