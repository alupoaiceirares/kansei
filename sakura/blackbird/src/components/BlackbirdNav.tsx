import { useEffect, useState, type CSSProperties } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { goToKansei, signOut } from '../auth/auth';
import { useSession } from '../session';
import { WtwLogoHover } from './WtwLogo';

const LINK_BASE: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  flexShrink: 0,
  height: 34,
  padding: '0 11px',
  borderRadius: 8,
  fontSize: 13.5,
  fontWeight: 500,
  letterSpacing: '0.01em',
  textDecoration: 'none',
  whiteSpace: 'nowrap',
  transition: 'color 140ms ease, background-color 140ms ease',
};

const MENU_ITEM: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 10,
  height: 34,
  padding: '0 11px',
  borderRadius: 7,
  fontSize: 13,
  color: COLORS.bodyOnCard,
  textDecoration: 'none',
  background: 'transparent',
  border: 'none',
  cursor: 'pointer',
  width: '100%',
  fontFamily: FONT_STACK,
};

const LINKS = [
  { label: 'Home', to: '/dashboard' },
  { label: 'Map', to: '/map' },
  { label: 'My flights', to: '/flights' },
  { label: 'Journeys', to: '/journeys' },
  { label: 'Stats', to: '/stats' },
  { label: 'Friends', to: '/friends' },
];

// Pages that already carry a flight search field do not repeat the orange Add flight button.
const SEARCH_ON_PAGE = ['/', '/dashboard', '/add'];

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return 'WT';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

function MenuItem({ label, to, onClick, later }: { label: string; to?: string; onClick?: () => void; later?: boolean }) {
  const hover = useHoverStyle(MENU_ITEM, { background: '#14414F', color: COLORS.text });
  const body = (
    <>
      <span>{label}</span>
      {later && (
        <span
          style={{
            padding: '2px 7px',
            borderRadius: 999,
            border: '1px solid ' + COLORS.laterBorder,
            color: COLORS.laterText,
            fontSize: 9.5,
            fontWeight: 600,
            letterSpacing: '0.08em',
          }}
        >
          LATER
        </span>
      )}
    </>
  );
  if (to) {
    return (
      <Link to={to} onClick={onClick} {...hover} role="menuitem">
        {body}
      </Link>
    );
  }
  return (
    <button type="button" onClick={onClick} role="menuitem" {...hover}>
      {body}
    </button>
  );
}

function SignOutItem() {
  const hover = useHoverStyle(
    { ...MENU_ITEM, color: COLORS.dangerText, justifyContent: 'flex-start' },
    { background: COLORS.dangerBg },
  );
  return (
    <button type="button" onClick={signOut} role="menuitem" {...hover}>
      Sign out
    </button>
  );
}

function AddFlightButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      height: 38,
      padding: '0 17px',
      borderRadius: 999,
      background: COLORS.orange,
      color: COLORS.textOnOrange,
      fontSize: 13,
      fontWeight: 700,
      letterSpacing: '0.01em',
      textDecoration: 'none',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <Link to="/add" {...hover}>
      <span style={{ fontSize: 16, lineHeight: 1, marginTop: -2 }}>+</span>
      Add flight
    </Link>
  );
}

// 96px bar: eye logo, page links, then the account button and its menu.
export function BlackbirdNav() {
  const location = useLocation();
  const { displayName, email, isAdmin, incomingRequests } = useSession();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    // The menu stays open until a click outside or Escape, it never closes on mouse-out.
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  const showAdd = !SEARCH_ON_PAGE.includes(location.pathname);

  return (
    <header
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 40,
        display: 'flex',
        alignItems: 'center',
        gap: 28,
        height: 96,
        padding: '0 28px',
        background: 'rgba(6,33,43,0.92)',
        backdropFilter: 'blur(10px)',
        borderBottom: '1px solid ' + COLORS.line,
        fontFamily: FONT_STACK,
      }}
    >
      <Link to="/dashboard" aria-label="WTW home" style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
        <WtwLogoHover />
      </Link>

      <nav
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          flex: '1 1 0',
          minWidth: 0,
          overflowX: 'auto',
          scrollbarWidth: 'thin',
        }}
      >
        {LINKS.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            style={({ isActive }) =>
              isActive
                ? { ...LINK_BASE, color: COLORS.text, background: COLORS.raised, boxShadow: 'inset 0 -2px 0 ' + COLORS.cyan }
                : { ...LINK_BASE, color: COLORS.textMuted }
            }
          >
            {link.label}
            {link.label === 'Friends' && incomingRequests > 0 && (
              <span
                title={incomingRequests + ' friend requests waiting'}
                style={{ width: 7, height: 7, borderRadius: '50%', background: COLORS.orange, marginLeft: 6, flexShrink: 0 }}
              />
            )}
          </NavLink>
        ))}
      </nav>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0 }}>
        {showAdd && <AddFlightButton />}
        <div style={{ position: 'relative' }}>
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 36,
              height: 36,
              borderRadius: '50%',
              border: '1px solid ' + (menuOpen ? COLORS.cyan : COLORS.lineStrong),
              background: COLORS.surface,
              color: menuOpen ? COLORS.text : COLORS.textMuted,
              fontSize: 12,
              fontWeight: 600,
              cursor: 'pointer',
              userSelect: 'none',
            }}
          >
            {initialsOf(displayName)}
          </button>

          {menuOpen && (
            <>
              <div onClick={() => setMenuOpen(false)} style={{ position: 'fixed', inset: 0, zIndex: 45 }} />
              <div
                role="menu"
                style={{
                  position: 'absolute',
                  top: 'calc(100% + 10px)',
                  right: 0,
                  width: 238,
                  padding: 7,
                  background: COLORS.raised,
                  border: '1px solid ' + COLORS.lineStrong,
                  borderRadius: 12,
                  boxShadow: '0 20px 44px rgba(0,0,0,0.5)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 1,
                  zIndex: 50,
                }}
              >
                <div
                  style={{
                    padding: '9px 11px 10px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 2,
                    borderBottom: '1px solid ' + COLORS.lineStrong,
                    marginBottom: 5,
                  }}
                >
                  <span style={{ fontSize: 13.5, fontWeight: 600, color: COLORS.text }}>{displayName}</span>
                  {email && <span style={{ fontSize: 11.5, color: COLORS.textMuted }}>{email}</span>}
                </div>

                <MenuItem label="My profile" to="/profile" onClick={() => setMenuOpen(false)} />
                {isAdmin && <MenuItem label="Admin" to="/admin" onClick={() => setMenuOpen(false)} />}
                <MenuItem label="Yearly recap" to="/recap" later onClick={() => setMenuOpen(false)} />
                <MenuItem label="Export my data" to="/profile#export" onClick={() => setMenuOpen(false)} />
                <MenuItem label="Back to Kansei" onClick={goToKansei} />

                <div style={{ height: 1, background: COLORS.lineStrong, margin: '5px 0' }} />
                <SignOutItem />
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
