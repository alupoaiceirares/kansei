import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Spinner } from '../components/Spinner';
import { FlightFlags, aircraftLabel, routeOf } from '../components/FlightFlags';
import { RouteMap, type RouteStop } from '../map/RouteMap';
import { fetchFriends, fetchJourneys } from '../api/tailwind';
import { fetchDashboardProfile, fetchFriendTotals, type DashboardProfile } from '../api/profile';
import type { Friend, Journey, UserFlight } from '../api/types';
import { useSession } from '../session';
import { daysUntil, earthLaps, formatDate, formatKm, formatMinutes, formatNumber, initialsOf } from '../format';

const CARD: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  background: COLORS.surfaceOverlay,
  border: '1px solid ' + COLORS.line,
  borderRadius: 14,
  overflow: 'hidden',
};

const CARD_HEAD: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 14,
  padding: '16px 20px',
  borderBottom: '1px solid ' + COLORS.line,
};

const ROW_GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '92px 72px minmax(0,1fr) minmax(0,0.9fr) 82px auto',
  gap: 14,
  alignItems: 'center',
  padding: '14px 20px',
  borderBottom: '1px solid ' + COLORS.lineSoft,
  fontSize: 13.5,
  textDecoration: 'none',
  color: 'inherit',
};

function SearchButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 9,
      flex: '0 0 auto',
      height: 46,
      padding: '0 24px',
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
    <button type="submit" {...hover}>
      <svg viewBox="0 0 16 16" width={15} height={15} fill="none" stroke={COLORS.textOnOrange} strokeWidth={1.9} aria-hidden="true">
        <circle cx={7} cy={7} r={4.6} />
        <path d="M10.6 10.6 L14 14" strokeLinecap="round" />
      </svg>
      Search
    </button>
  );
}

function RecentRow({ userFlight }: { userFlight: UserFlight }) {
  const hover = useHoverStyle(ROW_GRID, { background: COLORS.raised });
  return (
    <Link to={'/flights/' + userFlight.id} {...hover}>
      <span style={{ color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums' }}>{formatDate(userFlight.flight.flightDate)}</span>
      <span style={{ color: COLORS.text, fontWeight: 600, letterSpacing: '0.03em' }}>{userFlight.flight.flightNumber ?? '—'}</span>
      <span style={{ color: COLORS.text, fontWeight: 500 }}>{routeOf(userFlight)}</span>
      <span style={{ color: COLORS.textMuted }}>{aircraftLabel(userFlight)}</span>
      <span style={{ color: COLORS.text, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
        {formatNumber(Math.round(userFlight.flight.distanceKm))}
      </span>
      <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
        <FlightFlags userFlight={userFlight} />
      </span>
    </Link>
  );
}

function FriendRow({ friend, totals }: { friend: Friend; totals?: { flightCount: number; distanceKm: number } }) {
  const hover = useHoverStyle(
    {
      display: 'flex',
      alignItems: 'center',
      gap: 12,
      padding: '12px 20px',
      borderBottom: '1px solid ' + COLORS.lineSoft,
      textDecoration: 'none',
      color: 'inherit',
    },
    { background: COLORS.raised },
  );
  const meta = totals ? formatNumber(totals.flightCount) + ' flights · ' + formatKm(totals.distanceKm) : 'Log not shared';
  return (
    <Link to={'/users/' + friend.userId} {...hover}>
      <span
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 32,
          height: 32,
          borderRadius: '50%',
          background: COLORS.raised,
          border: '1px solid ' + COLORS.lineStrong,
          fontSize: 11.5,
          fontWeight: 600,
          color: COLORS.textMuted,
          flexShrink: 0,
        }}
      >
        {initialsOf(friend.username)}
      </span>
      <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
        <span style={{ fontSize: 13.5, fontWeight: 500 }}>{friend.username ?? 'Unknown user'}</span>
        <span style={{ fontSize: 11.5, color: COLORS.textDim }}>{meta}</span>
      </span>
    </Link>
  );
}

function AddFirstFlightButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 48,
      padding: '0 26px',
      borderRadius: 9,
      background: COLORS.orange,
      color: COLORS.textOnOrange,
      fontSize: 15,
      fontWeight: 700,
      textDecoration: 'none',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <Link to="/add" {...hover}>
      Add your first flight
    </Link>
  );
}

const PANEL_STOPS = 5;

/**
 * A taste of the log for the panel: up to five airports off the most recent flights, one per
 * country, chained with arcs. An empty log leaves the map drawn with nothing highlighted.
 */
function panelStops(flights: UserFlight[]): RouteStop[] {
  const byRecent = [...flights].sort((a, b) => b.flight.flightDate.localeCompare(a.flight.flightDate));
  const stops: RouteStop[] = [];
  const seen = new Set<string>();
  for (const userFlight of byRecent) {
    for (const airport of [userFlight.flight.departureAirport, userFlight.flight.arrivalAirport]) {
      if (seen.has(airport.countryCode)) continue;
      seen.add(airport.countryCode);
      stops.push({
        code: airport.iata ?? airport.icao ?? '',
        lon: airport.longitude,
        lat: airport.latitude,
        countryCode: airport.countryCode,
        kind: 'end',
      });
      if (stops.length === PANEL_STOPS) return stops;
    }
  }
  return stops;
}

export function DashboardPage() {
  const navigate = useNavigate();
  const { me, displayName } = useSession();
  const [profile, setProfile] = useState<DashboardProfile | null>(null);
  const [journeys, setJourneys] = useState<Journey[]>([]);
  const [friends, setFriends] = useState<Friend[]>([]);
  const [friendTotals, setFriendTotals] = useState<Record<string, { flightCount: number; distanceKm: number }>>({});
  const [loading, setLoading] = useState(true);
  const [flightNumber, setFlightNumber] = useState('');
  const [date, setDate] = useState('');

  useEffect(() => {
    let live = true;
    Promise.all([fetchDashboardProfile().catch(() => null), fetchJourneys().catch(() => []), fetchFriends().catch(() => [])])
      .then(([loadedProfile, loadedJourneys, loadedFriends]) => {
        if (!live) return;
        setProfile(loadedProfile);
        setJourneys(loadedJourneys);
        setFriends(loadedFriends);
        setLoading(false);
        const top = loadedFriends.slice(0, 3).map((friend) => friend.userId);
        if (top.length > 0) {
          fetchFriendTotals(top)
            .then((totals) => {
              if (!live) return;
              setFriendTotals(Object.fromEntries(totals.map((total) => [total.userId, total])));
            })
            .catch(() => undefined);
        }
      })
      .catch(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, []);

  const flights = useMemo(() => journeys.flatMap((journey) => journey.flights), [journeys]);

  const recent = useMemo(
    () =>
      flights
        .filter((userFlight) => !userFlight.flight.upcoming)
        .sort((a, b) => b.flight.flightDate.localeCompare(a.flight.flightDate))
        .slice(0, 4),
    [flights],
  );

  const nextUp = useMemo(
    () =>
      flights
        .filter((userFlight) => userFlight.flight.upcoming)
        .sort((a, b) => a.flight.flightDate.localeCompare(b.flight.flightDate))[0],
    [flights],
  );

  const mapStops = useMemo(() => panelStops(flights), [flights]);

  const search = (event: { preventDefault: () => void }) => {
    event.preventDefault();
    if (!flightNumber.trim() || !date) return;
    // A lookup costs a provider call, so it only ever happens from an explicit search.
    navigate('/add?flightNumber=' + encodeURIComponent(flightNumber.trim().toUpperCase()) + '&date=' + date);
  };

  if (loading) {
    return (
      <PageShell>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading your log
        </div>
      </PageShell>
    );
  }

  const stats = profile?.stats;
  const empty = !stats || stats.flightCount === 0;

  if (empty) {
    return (
      <PageShell>
        <div
          style={{
            maxWidth: 700,
            margin: '0 auto',
            padding: '96px 0',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 22,
            textAlign: 'center',
          }}
        >
          <svg viewBox="0 0 160 80" width={180} height={90} fill="none" aria-hidden="true">
            <path d="M8 64 C48 18 112 18 152 64" stroke={COLORS.lineStrong} strokeWidth={1.6} strokeDasharray="4 5" />
            <circle cx={8} cy={64} r={4} fill={COLORS.lineStrong} />
            <circle cx={152} cy={64} r={4} fill={COLORS.lineStrong} />
            <path
              transform="translate(80 30) scale(0.5) translate(-120 -66)"
              fill={COLORS.lineStrong}
              d="M120 40 L122.7 50.6 L140.3 64.7 L140.3 69.3 L122.7 63.1 L122.7 80.3 L129.4 86.5 L129.4 89.2 L121.4 84.9 L120.6 92 L119.4 92 L118.6 84.9 L110.6 89.2 L110.6 86.5 L117.3 80.3 L117.3 63.1 L99.7 69.3 L99.7 64.7 L117.3 50.6 Z"
            />
          </svg>
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 600, letterSpacing: '-0.02em' }}>Your log is empty</h1>
          <p style={{ margin: 0, fontSize: 15, lineHeight: 1.65, color: COLORS.textMuted, maxWidth: 420, textWrap: 'pretty' }}>
            Add one flight and this page becomes a map, a distance count and the first entries in your collections. Start with the
            last one you remember.
          </p>
          <AddFirstFlightButton />
        </div>
      </PageShell>
    );
  }

  const visited = profile.countries.visited.length;
  const transit = profile.countries.passedThrough.length;
  const topFamily = [...profile.aircraft].sort((a, b) => b.flightCount - a.flightCount)[0];
  const since = me ? new Date(me.joinedAt) : null;

  const statCards = [
    {
      label: 'Distance flown',
      value: formatKm(stats.distanceKm),
      note: earthLaps(stats.distanceKm) + ' times around the Earth',
      color: COLORS.text,
    },
    {
      label: 'Time in air',
      value: formatMinutes(stats.timeInAirMinutes),
      note: 'from ' + stats.flightsWithDuration + ' of ' + stats.flightCount + ' flights',
      color: COLORS.text,
    },
    { label: 'Countries', value: String(visited), note: 'plus ' + transit + ' in transit only', color: COLORS.cyan },
    {
      label: 'Aircraft families',
      value: String(stats.aircraftFamilyCount),
      note: topFamily ? topFamily.family + ' flown ' + topFamily.flightCount + ' times' : 'none recorded yet',
      color: COLORS.cyan,
    },
  ];

  const records = [
    profile.records.longestFlight && {
      label: 'Longest flight',
      value:
        (profile.records.longestFlight.departureIata ?? '?') +
        ' → ' +
        (profile.records.longestFlight.arrivalIata ?? '?') +
        ' · ' +
        formatKm(profile.records.longestFlight.distanceKm),
    },
    profile.records.shortestFlight && {
      label: 'Shortest flight',
      value:
        (profile.records.shortestFlight.departureIata ?? '?') +
        ' → ' +
        (profile.records.shortestFlight.arrivalIata ?? '?') +
        ' · ' +
        formatKm(profile.records.shortestFlight.distanceKm),
    },
    profile.records.mostFlownRoute && {
      label: 'Most flown route',
      value:
        (profile.records.mostFlownRoute.departureIata ?? '?') +
        ' → ' +
        (profile.records.mostFlownRoute.arrivalIata ?? '?') +
        ' · ' +
        profile.records.mostFlownRoute.flightCount +
        ' times',
    },
    profile.records.busiestMonth && {
      label: 'Busiest month',
      value: profile.records.busiestMonth.label + ' · ' + profile.records.busiestMonth.count + ' flights',
    },
    profile.records.biggestYear && {
      label: 'Biggest year',
      value: profile.records.biggestYear.label + ' · ' + formatKm(profile.records.biggestYear.distanceKm),
    },
  ].filter(Boolean) as { label: string; value: string }[];

  return (
    <PageShell maxWidth={1240}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 18, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ fontSize: 11, letterSpacing: '0.2em', textTransform: 'uppercase', color: COLORS.orange }}>Welcome back</div>
          <h1 style={{ margin: 0, fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em' }}>{displayName}</h1>
        </div>
        <div style={{ fontSize: 13, color: COLORS.textDim }}>
          {formatNumber(stats.flightCount)} flights logged
          {since ? ' · since ' + since.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' }) : ''}
        </div>
      </div>

      <form
        onSubmit={search}
        style={{
          width: '100%',
          display: 'flex',
          flexWrap: 'wrap',
          gap: 10,
          alignItems: 'center',
          padding: 14,
          background: COLORS.surfaceOverlayStrong,
          border: '1px solid ' + COLORS.lineStrong,
          borderRadius: 13,
          boxSizing: 'border-box',
        }}
      >
        <span
          style={{
            fontSize: 11,
            letterSpacing: '0.16em',
            textTransform: 'uppercase',
            color: COLORS.textDim,
            flex: '0 0 auto',
            paddingLeft: 4,
          }}
        >
          Log a flight
        </span>
        <input
          type="text"
          value={flightNumber}
          onChange={(event) => setFlightNumber(event.target.value)}
          placeholder="Flight number, e.g. LH400"
          spellCheck={false}
          style={{
            flex: '2 1 200px',
            minWidth: 0,
            height: 46,
            padding: '0 15px',
            borderRadius: 9,
            background: COLORS.ground,
            border: '1px solid ' + COLORS.lineStrong,
            fontSize: 15,
            color: COLORS.text,
            letterSpacing: '0.03em',
            outline: 'none',
          }}
        />
        <input
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
          style={{
            flex: '1 1 160px',
            minWidth: 0,
            height: 46,
            padding: '0 15px',
            borderRadius: 9,
            background: COLORS.ground,
            border: '1px solid ' + COLORS.lineStrong,
            fontSize: 14,
            color: COLORS.text,
            outline: 'none',
            colorScheme: 'dark',
          }}
        />
        <SearchButton />
        <Link to="/add/manual" style={{ fontSize: 12.5, color: COLORS.cyan, paddingLeft: 4 }}>
          or add it by hand
        </Link>
      </form>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 16 }}>
        {statCards.map((stat) => (
          <div
            key={stat.label}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 7,
              padding: '20px 22px',
              background: COLORS.surfaceOverlay,
              border: '1px solid ' + COLORS.line,
              borderRadius: 13,
            }}
          >
            <span style={{ fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim }}>{stat.label}</span>
            <span style={{ fontSize: 29, fontWeight: 600, fontVariantNumeric: 'tabular-nums', lineHeight: 1.05, color: stat.color }}>
              {stat.value}
            </span>
            <span style={{ fontSize: 12.5, color: COLORS.textMuted }}>{stat.note}</span>
          </div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.5fr) minmax(0,1fr)', gap: 22, alignItems: 'start' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <div style={CARD}>
            <div style={CARD_HEAD}>
              <span style={{ fontSize: 16, fontWeight: 600 }}>Recent flights</span>
              <Link to="/flights" style={{ fontSize: 13 }}>
                See all {formatNumber(stats.flightCount)} →
              </Link>
            </div>
            {recent.map((userFlight) => (
              <RecentRow key={userFlight.id} userFlight={userFlight} />
            ))}
          </div>

          <div style={CARD}>
            <div style={CARD_HEAD}>
              <span style={{ fontSize: 16, fontWeight: 600 }}>Where you have been</span>
              <Link to="/map" style={{ fontSize: 13 }}>
                Open the map →
              </Link>
            </div>
            <Link to="/map" style={{ position: 'relative', display: 'block', height: 268, background: '#071D28', textDecoration: 'none' }}>
              <RouteMap stops={mapStops} />
              <span
                style={{
                  position: 'absolute',
                  left: 18,
                  bottom: 16,
                  display: 'flex',
                  gap: 16,
                  fontSize: 12,
                  color: COLORS.bodyOnCard,
                  zIndex: 2,
                  background: 'rgba(6,33,43,0.8)',
                  padding: '6px 12px',
                  borderRadius: 999,
                }}
              >
                <span>{visited} countries visited</span>
                <span>{transit} in transit only</span>
                <span>{stats.airportCount} airports</span>
              </span>
            </Link>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          {nextUp && (
            <div style={{ ...CARD, border: '1px solid #7A4A16' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '15px 18px',
                  borderBottom: '1px solid #7A4A16',
                  background: 'rgba(46,29,12,0.5)',
                }}
              >
                <span
                  style={{
                    padding: '3px 9px',
                    borderRadius: 999,
                    background: '#2E1D0C',
                    border: '1px solid #7A4A16',
                    color: '#FFB067',
                    fontSize: 10.5,
                    fontWeight: 600,
                    letterSpacing: '0.07em',
                  }}
                >
                  UPCOMING
                </span>
                <span style={{ fontSize: 14, fontWeight: 600 }}>{daysUntil(nextUp.flight.flightDate)}</span>
              </div>
              <div style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 13 }}>
                <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10 }}>
                  <span style={{ fontSize: 19, fontWeight: 700, letterSpacing: '0.03em' }}>{nextUp.flight.flightNumber ?? 'Manual entry'}</span>
                  <span style={{ fontSize: 12.5, color: COLORS.textMuted }}>{formatDate(nextUp.flight.flightDate)}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <span style={{ fontSize: 24, fontWeight: 700 }}>
                    {nextUp.flight.departureAirport.iata ?? nextUp.flight.departureAirport.icao}
                  </span>
                  <svg viewBox="0 0 56 20" width={56} height={20} fill="none" aria-hidden="true">
                    <path d="M2 15 C16 3 40 3 54 15" stroke={COLORS.lineStrong} strokeWidth={1.3} strokeDasharray="3 4" />
                    <circle cx={2} cy={15} r={2.5} fill={COLORS.cyan} />
                    <circle cx={54} cy={15} r={2.5} fill={COLORS.cyan} />
                  </svg>
                  <span style={{ fontSize: 24, fontWeight: 700 }}>{nextUp.flight.arrivalAirport.iata ?? nextUp.flight.arrivalAirport.icao}</span>
                </div>
                <div style={{ fontSize: 12.5, color: COLORS.textMuted }}>
                  Aircraft{' '}
                  <span style={{ color: COLORS.text }}>
                    {nextUp.flight.aircraft.typeName ?? nextUp.flight.aircraft.family ?? 'not announced yet'}
                  </span>
                  {nextUp.flight.awaitingRefresh && ' · scheduled, may change'}
                </div>
                <div style={{ fontSize: 12.5, lineHeight: 1.55, color: COLORS.textMuted }}>
                  Not counted in stats, records or the map until it has happened.
                </div>
                <Link to={'/flights/' + nextUp.id} style={{ fontSize: 13, fontWeight: 600 }}>
                  Open flight →
                </Link>
              </div>
            </div>
          )}

          <div style={CARD}>
            <div style={CARD_HEAD}>
              <span style={{ fontSize: 16, fontWeight: 600 }}>Friends</span>
              <Link to="/friends" style={{ fontSize: 13 }}>
                See all →
              </Link>
            </div>
            {friends.length === 0 ? (
              <div style={{ padding: '18px 20px', fontSize: 13, color: COLORS.textMuted }}>
                No friends yet. Search for someone on the friends page.
              </div>
            ) : (
              friends.slice(0, 3).map((friend) => <FriendRow key={friend.userId} friend={friend} totals={friendTotals[friend.userId]} />)
            )}
          </div>

          <div style={CARD}>
            <div style={CARD_HEAD}>
              <span style={{ fontSize: 16, fontWeight: 600 }}>Your records</span>
              <Link to="/stats" style={{ fontSize: 13 }}>
                All stats →
              </Link>
            </div>
            {records.map((record) => (
              <div
                key={record.label}
                style={{
                  display: 'flex',
                  alignItems: 'baseline',
                  justifyContent: 'space-between',
                  gap: 12,
                  padding: '13px 20px',
                  borderBottom: '1px solid ' + COLORS.lineSoft,
                }}
              >
                <span style={{ fontSize: 12.5, color: COLORS.textMuted }}>{record.label}</span>
                <span style={{ fontSize: 13.5, fontWeight: 600, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{record.value}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </PageShell>
  );
}
