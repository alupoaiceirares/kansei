import type { CSSProperties, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { COLORS, PAGE_MAX_WIDTH } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { NORTHSTAR_URL, SOUNDWAVE_URL } from '../config';
import { WtwLogo } from './WtwLogo';

const COLUMN_LABEL: CSSProperties = {
  fontSize: 11,
  letterSpacing: '0.16em',
  textTransform: 'uppercase',
  color: COLORS.textDim,
};

const COLUMN_LINK: CSSProperties = { fontSize: 13, color: COLORS.bodyOnCard, textDecoration: 'none' };

function FooterLink({ to, children }: { to: string; children: ReactNode }) {
  const hover = useHoverStyle(COLUMN_LINK, { color: COLORS.cyanBright });
  return (
    <Link to={to} {...hover}>
      {children}
    </Link>
  );
}

function ExternalLink({ href, children }: { href: string; children: ReactNode }) {
  const hover = useHoverStyle(COLUMN_LINK, { color: COLORS.cyanBright });
  return (
    <a href={href} {...hover}>
      {children}
    </a>
  );
}

function BackToKansei() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      height: 36,
      padding: '0 15px',
      borderRadius: 999,
      border: '1px solid ' + COLORS.lineStrong,
      color: COLORS.text,
      fontSize: 12.5,
      fontWeight: 500,
      textDecoration: 'none',
      whiteSpace: 'nowrap',
    },
    { borderColor: COLORS.cyan },
  );
  return (
    <a href={NORTHSTAR_URL} {...hover}>
      <span style={{ fontSize: 15, lineHeight: 1, marginTop: -1 }}>&larr;</span>Back to Kansei
    </a>
  );
}

function WirehoodPill() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 9,
      height: 36,
      padding: '0 15px',
      borderRadius: 999,
      border: '1px solid ' + COLORS.line,
      color: COLORS.textMuted,
      fontSize: 12.5,
      fontWeight: 500,
      textDecoration: 'none',
      whiteSpace: 'nowrap',
    },
    { borderColor: COLORS.cyan, color: COLORS.text },
  );
  return (
    <a href={SOUNDWAVE_URL} {...hover}>
      <svg viewBox="0 0 24 17" width="16" height="11" fill="none" aria-hidden="true">
        <path d="M5 11 A 7 6.3 0 0 1 19 11" stroke="#12B76B" strokeWidth={2} strokeLinecap="round" />
        <path d="M19 11 C19 13.4, 20.2 14.6, 22.4 14.6" stroke="#12B76B" strokeWidth={2} strokeLinecap="round" />
        <circle cx={5} cy={11} r={1.9} fill="#12B76B" />
        <circle cx={19} cy={11} r={1.9} fill="#E23A3A" />
      </svg>
      Wirehood
    </a>
  );
}

function BottomLink({ children }: { children: ReactNode }) {
  const hover = useHoverStyle({ fontSize: 12, color: COLORS.textMuted, textDecoration: 'none' }, { color: COLORS.cyanBright });
  return (
    <a href="#" {...hover}>
      {children}
    </a>
  );
}

// Identical on every page: four columns, then the copyright bar with the Kansei and Wirehood pills.
export function Footer() {
  return (
    <footer style={{ position: 'relative', borderTop: '1px solid ' + COLORS.line, marginTop: 'auto', background: 'rgba(8,40,52,0.6)' }}>
      <div
        style={{
          maxWidth: PAGE_MAX_WIDTH,
          margin: '0 auto',
          padding: '34px 32px 26px',
          display: 'grid',
          gridTemplateColumns: 'minmax(0,1.3fr) repeat(3, minmax(0,1fr))',
          gap: 28,
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, minWidth: 0 }}>
          <WtwLogo width={92} height={51} title="World Travel Watcher" />
          <span style={{ fontSize: 13, lineHeight: 1.6, color: COLORS.textMuted, maxWidth: 280 }}>
            World Travel Watcher &mdash; part of the Kansei Project.
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 9, flexWrap: 'wrap', marginTop: 2 }}>
            <BackToKansei />
            <WirehoodPill />
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0 }}>
          <span style={COLUMN_LABEL}>Your log</span>
          <FooterLink to="/dashboard">Home</FooterLink>
          <FooterLink to="/flights">My flights</FooterLink>
          <FooterLink to="/journeys">Journeys</FooterLink>
          <FooterLink to="/map">Map</FooterLink>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0 }}>
          <span style={COLUMN_LABEL}>Explore</span>
          <FooterLink to="/stats">Stats</FooterLink>
          <FooterLink to="/friends">Friends</FooterLink>
          <FooterLink to="/recap">Yearly recap</FooterLink>
          <FooterLink to="/add">Add a flight</FooterLink>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0 }}>
          <span style={COLUMN_LABEL}>Account</span>
          <FooterLink to="/profile">My profile</FooterLink>
          <FooterLink to="/profile#privacy">Privacy</FooterLink>
          <FooterLink to="/profile#export">Export my data</FooterLink>
          <ExternalLink href={NORTHSTAR_URL + '/profile'}>Kansei account</ExternalLink>
        </div>
      </div>

      <div style={{ borderTop: '1px solid ' + COLORS.lineSoft }}>
        <div
          style={{
            maxWidth: PAGE_MAX_WIDTH,
            margin: '0 auto',
            padding: '16px 32px 22px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 16,
            flexWrap: 'wrap',
          }}
        >
          <span style={{ fontSize: 12, color: COLORS.textDim }}>&copy; 2026 Kansei Project</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexWrap: 'wrap' }}>
            <BottomLink>Privacy</BottomLink>
            <BottomLink>Terms</BottomLink>
            <BottomLink>Data sources</BottomLink>
            <span style={{ fontSize: 12, color: COLORS.textMuted }}>
              Flight data via provider &middot; aircraft photos via Wikimedia Commons
            </span>
          </div>
        </div>
      </div>
    </footer>
  );
}
