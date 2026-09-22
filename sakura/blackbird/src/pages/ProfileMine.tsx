import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { COLORS, FONT_STACK, VISIBILITY_PILL, upcomingPill } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import { Segmented } from '../components/Segmented';
import { ApiError } from '../api/client';
import { deleteOwnLog, fetchFriendRequests, fetchFriends, fetchJourneys, updateDefaultVisibility } from '../api/tailwind';
import { fetchDashboardProfile, type DashboardProfile } from '../api/profile';
import type { Journey, UserFlight, Visibility } from '../api/types';
import { aircraftLabel, routeOf } from '../components/FlightFlags';
import { useSession } from '../session';
import { NORTHSTAR_URL } from '../config';
import { earthLaps, formatKm, formatNumber, initialsOf } from '../format';

const VISIBILITIES = [
  { value: 'PUBLIC' as const, label: 'PUBLIC' },
  { value: 'FRIENDS' as const, label: 'FRIENDS' },
  { value: 'PRIVATE' as const, label: 'PRIVATE' },
];

const VIS_HINT: Record<Visibility, string> = {
  PUBLIC: 'Every flight you add from now on is visible to anyone who opens your profile.',
  FRIENDS: 'Every flight you add from now on is visible to your friends only. This is the default so nothing becomes public by accident.',
  PRIVATE: 'Every flight you add from now on is yours alone. They still count in your own stats, map and records.',
};

const CARD: CSSProperties = {
  background: COLORS.surfaceOverlay,
  border: '1px solid ' + COLORS.line,
  borderRadius: 14,
  overflow: 'hidden',
};

const CARD_HEAD: CSSProperties = { padding: '16px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 };

function AddFlightButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 7,
      height: 42,
      padding: '0 20px',
      borderRadius: 9,
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
    <Link to="/add" {...hover}>
      <span style={{ fontSize: 16, lineHeight: 1, marginTop: -2 }}>+</span>Add flight
    </Link>
  );
}

function GhostLink({ to, children, external = false }: { to: string; children: string; external?: boolean }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 42,
      padding: '0 20px',
      borderRadius: 9,
      border: '1px solid ' + COLORS.lineStrong,
      color: COLORS.textMuted,
      fontSize: 13.5,
      fontWeight: 500,
      textDecoration: 'none',
      whiteSpace: 'nowrap',
    },
    { color: COLORS.text, borderColor: COLORS.cyan },
  );
  if (external) {
    return (
      <a href={to} {...hover}>
        {children}
      </a>
    );
  }
  return (
    <Link to={to} {...hover}>
      {children}
    </Link>
  );
}

function ExportRow({ title, note, onClick }: { title: string; note: string; onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'flex',
      alignItems: 'center',
      gap: 13,
      padding: '13px 15px',
      borderRadius: 10,
      background: '#082834',
      border: '1px solid ' + COLORS.line,
      cursor: 'pointer',
      textAlign: 'left' as const,
      width: '100%',
      fontFamily: FONT_STACK,
    },
    { borderColor: COLORS.cyan, background: COLORS.raised },
  );
  return (
    <button type="button" onClick={onClick} {...hover}>
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 34,
          height: 34,
          borderRadius: 8,
          background: COLORS.raised,
          border: '1px solid ' + COLORS.lineStrong,
          flexShrink: 0,
        }}
      >
        <svg viewBox="0 0 24 24" width={16} height={16} fill="none" stroke="#7FCFE8" strokeWidth={2} aria-hidden="true">
          <path d="M12 4 v10" strokeLinecap="round" />
          <path d="M7.5 10 L12 14.5 L16.5 10" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M5 17 v1.5 a1.5 1.5 0 0 0 1.5 1.5 h11 a1.5 1.5 0 0 0 1.5 -1.5 V17" />
        </svg>
      </span>
      <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0, flex: '1 1 auto' }}>
        <span style={{ fontSize: 13.5, fontWeight: 600, color: COLORS.text }}>{title}</span>
        <span style={{ fontSize: 11.5, color: COLORS.textMuted, lineHeight: 1.5 }}>{note}</span>
      </span>
      <span style={{ fontSize: 12.5, fontWeight: 600, color: COLORS.cyan, whiteSpace: 'nowrap' }}>Download</span>
    </button>
  );
}

function DeleteButton({ onClick }: { onClick: () => void }) {
  const hover = useHoverStyle(
    {
      alignSelf: 'flex-start',
      display: 'inline-flex',
      alignItems: 'center',
      height: 38,
      padding: '0 16px',
      borderRadius: 8,
      border: '1px solid ' + COLORS.dangerBorder,
      background: 'transparent',
      color: COLORS.dangerText,
      fontFamily: FONT_STACK,
      fontSize: 13,
      fontWeight: 600,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.dangerBg },
  );
  return (
    <button type="button" onClick={onClick} {...hover}>
      Delete my log
    </button>
  );
}

function download(name: string, type: string, body: string): void {
  const url = URL.createObjectURL(new Blob([body], { type }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

/** CSV values are quoted, a note with a comma in it must not shift the columns. */
function csvCell(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return '';
  return '"' + String(value).replace(/"/g, '""') + '"';
}

export function ProfileMinePage() {
  const navigate = useNavigate();
  const { me, displayName, email, incomingRequests, refreshMe } = useSession();

  const [profile, setProfile] = useState<DashboardProfile | null>(null);
  const [journeys, setJourneys] = useState<Journey[]>([]);
  const [friendCount, setFriendCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [visibility, setVisibility] = useState<Visibility>(me?.defaultVisibility ?? 'FRIENDS');
  const [savingVisibility, setSavingVisibility] = useState(false);
  const [exported, setExported] = useState<string | null>(null);
  const [askDelete, setAskDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    Promise.all([
      fetchDashboardProfile().catch(() => null),
      fetchJourneys().catch(() => []),
      fetchFriends().catch(() => []),
      fetchFriendRequests().catch(() => []),
    ])
      .then(([loadedProfile, loadedJourneys, friends]) => {
        if (!live) return;
        setProfile(loadedProfile);
        setJourneys(loadedJourneys);
        setFriendCount(friends.length);
      })
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, []);

  useEffect(() => {
    if (me) setVisibility(me.defaultVisibility);
  }, [me]);

  const flights = useMemo(() => journeys.flatMap((journey) => journey.flights), [journeys]);

  const counts = useMemo(() => {
    const byVisibility = { PUBLIC: 0, FRIENDS: 0, PRIVATE: 0 } as Record<Visibility, number>;
    let upcoming = 0;
    for (const flight of flights) {
      byVisibility[flight.visibility] += 1;
      if (flight.flight.upcoming) upcoming += 1;
    }
    return { byVisibility, upcoming };
  }, [flights]);

  const saveVisibility = async (next: Visibility) => {
    setVisibility(next);
    setSavingVisibility(true);
    setError(null);
    try {
      await updateDefaultVisibility(next);
      await refreshMe();
    } catch (cause) {
      setVisibility(me?.defaultVisibility ?? 'FRIENDS');
      setError(cause instanceof ApiError ? (cause.detail ?? 'The default could not be saved.') : 'The default could not be saved.');
    } finally {
      setSavingVisibility(false);
    }
  };

  const exportJson = () => {
    const body = {
      exported: new Date().toISOString(),
      account: { name: displayName, email, managedBy: 'Kansei' },
      totals: profile
        ? {
            flights: profile.stats.flightCount,
            distanceKm: Math.round(profile.stats.distanceKm),
            minutesInAir: profile.stats.timeInAirMinutes,
            countries: profile.countries.visited.length,
            journeys: journeys.length,
          }
        : null,
      journeys: journeys.map((journey) => ({
        id: journey.id,
        title: journey.title,
        notes: journey.notes,
        flights: journey.flights.map(exportFlight),
      })),
    };
    download('wtw-my-log.json', 'application/json', JSON.stringify(body, null, 2));
    setExported('Downloaded wtw-my-log.json');
  };

  const exportCsv = () => {
    const head = 'date,number,from,to,airline,aircraft,registration,distance_km,seat,position,cabin,reason,visibility,journey,notes';
    const rows = flights.map((flight) =>
      [
        flight.flight.flightDate,
        flight.flight.flightNumber,
        flight.flight.departureAirport.iata ?? flight.flight.departureAirport.icao,
        flight.flight.arrivalAirport.iata ?? flight.flight.arrivalAirport.icao,
        flight.flight.airline.name,
        aircraftLabel(flight),
        flight.flight.aircraft.registration,
        Math.round(flight.flight.distanceKm),
        flight.seat,
        flight.seatPosition,
        flight.cabinClass,
        flight.reason,
        flight.visibility,
        journeys.find((journey) => journey.id === flight.journeyId)?.title,
        flight.notes,
      ]
        .map(csvCell)
        .join(','),
    );
    download('wtw-my-log.csv', 'text/csv', [head, ...rows].join('\n'));
    setExported('Downloaded wtw-my-log.csv');
  };

  const removeLog = async () => {
    setDeleting(true);
    try {
      await deleteOwnLog();
      await refreshMe();
      navigate('/opt-in', { replace: true });
    } catch (cause) {
      setAskDelete(false);
      setDeleting(false);
      setError(cause instanceof ApiError ? (cause.detail ?? 'The log could not be deleted.') : 'The log could not be deleted.');
    }
  };

  if (loading) {
    return (
      <PageShell maxWidth={1100}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading your profile
        </div>
      </PageShell>
    );
  }

  const stats = [
    {
      label: 'Distance',
      value: profile ? formatKm(profile.stats.distanceKm) : '—',
      note: profile ? earthLaps(profile.stats.distanceKm) + ' times around' : '',
      color: COLORS.text,
    },
    {
      label: 'Flights',
      value: profile ? formatNumber(profile.stats.flightCount) : '—',
      note: counts.upcoming > 0 ? counts.upcoming + ' upcoming' : 'all flown',
      color: COLORS.text,
    },
    {
      label: 'Countries',
      value: profile ? formatNumber(profile.countries.visited.length) : '—',
      note: profile ? 'plus ' + profile.countries.passedThrough.length + ' in transit' : '',
      color: COLORS.cyan,
    },
    {
      label: 'Friends',
      value: formatNumber(friendCount),
      note: incomingRequests > 0 ? incomingRequests + ' requests waiting' : 'no requests waiting',
      color: '#FFB067',
    },
  ];

  const breakdown = [
    { pill: 'PUBLIC', style: VISIBILITY_PILL.PUBLIC, count: counts.byVisibility.PUBLIC, note: 'anyone can see these' },
    {
      pill: 'FRIENDS',
      style: VISIBILITY_PILL.FRIENDS,
      count: counts.byVisibility.FRIENDS,
      note: 'your ' + friendCount + (friendCount === 1 ? ' friend' : ' friends') + ' can see these',
    },
    { pill: 'PRIVATE', style: VISIBILITY_PILL.PRIVATE, count: counts.byVisibility.PRIVATE, note: 'only you, but still counted' },
    { pill: 'UPCOMING', style: upcomingPill, count: counts.upcoming, note: 'not in stats until flown' },
  ];

  return (
    <PageShell maxWidth={1100}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 20,
          flexWrap: 'wrap',
          padding: '24px 26px',
          background: COLORS.surfaceOverlay,
          border: '1px solid ' + COLORS.line,
          borderRadius: 14,
        }}
      >
        <span
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 72,
            height: 72,
            borderRadius: '50%',
            background: '#2E1D0C',
            border: '1px solid #7A4A16',
            fontSize: 22,
            fontWeight: 600,
            color: '#FFB067',
            flexShrink: 0,
          }}
        >
          {initialsOf(displayName)}
        </span>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 5, minWidth: 0, flex: '1 1 220px' }}>
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 600, letterSpacing: '-0.02em' }}>{displayName}</h1>
          <span style={{ fontSize: 13.5, color: COLORS.textDim }}>
            {me ? 'on WTW since ' + new Date(me.joinedAt).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' }) : ''}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 9, flexWrap: 'wrap' }}>
          {me && <GhostLink to={'/users/' + me.userId}>See how friends view me</GhostLink>}
          <AddFlightButton />
        </div>
      </div>

      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 16 }}>
        {stats.map((stat) => (
          <div
            key={stat.label}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              padding: '18px 20px',
              background: COLORS.surfaceOverlay,
              border: '1px solid ' + COLORS.line,
              borderRadius: 13,
            }}
          >
            <span style={{ fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim }}>{stat.label}</span>
            <span style={{ fontSize: 25, fontWeight: 600, fontVariantNumeric: 'tabular-nums', lineHeight: 1.05, color: stat.color }}>
              {stat.value}
            </span>
            <span style={{ fontSize: 12, color: COLORS.textMuted }}>{stat.note}</span>
          </div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', gap: 20, alignItems: 'start' }}>
        <div style={{ ...CARD, border: '1px solid ' + COLORS.lineStrong }} id="privacy">
          <div style={CARD_HEAD}>Privacy</div>
          <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
              <span style={{ fontSize: 12.5, fontWeight: 500, color: COLORS.bodyOnCard }}>Default for new flights</span>
              <Segmented options={VISIBILITIES} value={visibility} onChange={(value) => value && saveVisibility(value)} columns={3} visibility />
              <span style={{ fontSize: 11.5, lineHeight: 1.55, color: COLORS.textDim }}>
                {savingVisibility ? 'Saving…' : VIS_HINT[visibility]}
              </span>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 9, paddingTop: 18, borderTop: '1px solid ' + COLORS.lineSoft }}>
              <span style={{ fontSize: 12.5, fontWeight: 500, color: COLORS.bodyOnCard }}>Flights already logged</span>
              <span style={{ fontSize: 11.5, lineHeight: 1.55, color: COLORS.textDim }}>
                Changing the default never rewrites a flight you already added. Open a flight to change that one, or use the
                journey screen to set every leg of a trip at once.
              </span>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div style={CARD}>
            <div style={CARD_HEAD}>What your log contains</div>
            {breakdown.map((row) => (
              <div
                key={row.pill}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 20px', borderBottom: '1px solid ' + COLORS.lineSoft }}
              >
                <span style={row.style}>{row.pill}</span>
                <span style={{ fontSize: 13, color: COLORS.textMuted, flex: '1 1 auto' }}>{row.note}</span>
                <span style={{ fontSize: 13.5, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>{row.count}</span>
              </div>
            ))}
          </div>

          <div style={CARD}>
            <div style={CARD_HEAD}>Account</div>
            <div style={{ padding: '17px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 9, fontSize: 13 }}>
                <div style={{ display: 'flex', gap: 12 }}>
                  <span style={{ color: COLORS.textDim, width: 96, flexShrink: 0 }}>Signed in as</span>
                  <span style={{ color: COLORS.text }}>{email ?? displayName}</span>
                </div>
                <div style={{ display: 'flex', gap: 12 }}>
                  <span style={{ color: COLORS.textDim, width: 96, flexShrink: 0 }}>Managed by</span>
                  <span style={{ color: COLORS.text }}>Kansei</span>
                </div>
              </div>
              <div style={{ fontSize: 12.5, lineHeight: 1.6, color: COLORS.textMuted, paddingTop: 12, borderTop: '1px solid ' + COLORS.lineSoft }}>
                Your name, email and password live in your Kansei account. Change them there and WTW follows.
              </div>
              <div style={{ display: 'flex', gap: 9, flexWrap: 'wrap' }}>
                <a
                  href={NORTHSTAR_URL + '/profile'}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    height: 38,
                    padding: '0 16px',
                    borderRadius: 8,
                    border: '1px solid ' + COLORS.cyan,
                    color: COLORS.cyan,
                    fontSize: 13,
                    fontWeight: 600,
                    textDecoration: 'none',
                    whiteSpace: 'nowrap',
                  }}
                >
                  Kansei account
                </a>
              </div>
            </div>
          </div>

          <div style={{ ...CARD, border: '1px solid ' + COLORS.lineStrong }} id="export">
            <div style={CARD_HEAD}>Export my data</div>
            <div style={{ padding: '18px 20px', display: 'flex', flexDirection: 'column', gap: 15 }}>
              <div style={{ fontSize: 13, lineHeight: 1.6, color: COLORS.bodyOnCard }}>
                Everything in your log, including private flights: all {flights.length} flights with dates, routes, airlines,
                aircraft, seats, cabins and notes, plus your {journeys.length} journeys. Yours to keep, nothing is deleted.
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
                <ExportRow title="JSON" note="Complete structured export — every field WTW stores." onClick={exportJson} />
                <ExportRow title="CSV" note="One row per flight, opens in any spreadsheet." onClick={exportCsv} />
              </div>
              {exported && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '11px 14px',
                    borderRadius: 9,
                    background: '#0F2E20',
                    border: '1px solid #2C6B4C',
                  }}
                >
                  <span style={{ width: 7, height: 7, borderRadius: '50%', background: COLORS.success, flexShrink: 0 }} />
                  <span style={{ fontSize: 12.5, color: COLORS.bodyOnCard }}>{exported}</span>
                </div>
              )}
            </div>
          </div>

          <div style={{ ...CARD, border: '1px solid ' + COLORS.dangerBorder }}>
            <div style={{ ...CARD_HEAD, borderBottom: '1px solid ' + COLORS.dangerBorder, color: COLORS.dangerText }}>Delete my log</div>
            <div style={{ padding: '17px 20px', display: 'flex', flexDirection: 'column', gap: 13 }}>
              <div style={{ fontSize: 13, lineHeight: 1.6, color: COLORS.bodyOnCard }}>
                Removes all {flights.length} flights, your journeys, stats and collections. Your Kansei account is untouched, and
                you could opt in again from scratch.
              </div>
              <DeleteButton onClick={() => setAskDelete(true)} />
            </div>
          </div>
        </div>
      </div>

      {askDelete && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 60,
            background: 'rgba(3,14,19,0.72)',
            backdropFilter: 'blur(3px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 32,
          }}
        >
          <div
            style={{
              width: '100%',
              maxWidth: 460,
              padding: 26,
              background: COLORS.raised,
              border: '1px solid ' + COLORS.dangerBorder,
              borderRadius: 14,
              boxShadow: '0 30px 70px rgba(0,0,0,0.6)',
              display: 'flex',
              flexDirection: 'column',
              gap: 14,
            }}
          >
            <div style={{ fontSize: 18, fontWeight: 600 }}>Delete your whole log?</div>
            <p style={{ margin: 0, fontSize: 14, lineHeight: 1.65, color: COLORS.bodyOnCard }}>
              All {flights.length} flights, {journeys.length} journeys and everything built from them will be gone. Friends keep
              their own logs. This cannot be undone.
            </p>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 4, flexWrap: 'wrap' }}>
              <button
                type="button"
                onClick={() => setAskDelete(false)}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  height: 40,
                  padding: '0 18px',
                  borderRadius: 8,
                  border: '1px solid ' + COLORS.lineStrong,
                  background: 'transparent',
                  color: COLORS.textMuted,
                  fontFamily: FONT_STACK,
                  fontSize: 13.5,
                  cursor: 'pointer',
                }}
              >
                Keep my log
              </button>
              <button
                type="button"
                onClick={removeLog}
                disabled={deleting}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 9,
                  height: 40,
                  padding: '0 18px',
                  borderRadius: 8,
                  background: '#C2454F',
                  border: 'none',
                  color: '#FFE8EA',
                  fontFamily: FONT_STACK,
                  fontSize: 13.5,
                  fontWeight: 700,
                  cursor: 'pointer',
                }}
              >
                {deleting && <Spinner size={13} color="#FFE8EA" />}
                Delete everything
              </button>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}

function exportFlight(flight: UserFlight) {
  return {
    date: flight.flight.flightDate,
    number: flight.flight.flightNumber,
    from: flight.flight.departureAirport.iata ?? flight.flight.departureAirport.icao,
    to: flight.flight.arrivalAirport.iata ?? flight.flight.arrivalAirport.icao,
    airline: flight.flight.airline.name,
    aircraft: aircraftLabel(flight),
    registration: flight.flight.aircraft.registration,
    route: routeOf(flight),
    distanceKm: Math.round(flight.flight.distanceKm),
    seat: flight.seat,
    seatPosition: flight.seatPosition,
    cabinClass: flight.cabinClass,
    reason: flight.reason,
    visibility: flight.visibility,
    notes: flight.notes,
  };
}
