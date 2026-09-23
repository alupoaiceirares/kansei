import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Spinner } from '../components/Spinner';
import { FlightFlags, aircraftLabel, routeOf } from '../components/FlightFlags';
import { fetchMyFlights } from '../api/tailwind';
import type { UserFlight } from '../api/types';
import { formatDate, formatKm, formatNumber } from '../format';

const FILTERS = ['All', 'Past', 'Upcoming', 'Longest', 'Newest', 'Oldest', 'Cargo'] as const;
type Filter = (typeof FILTERS)[number];

const PAGE_SIZE = 9;

const GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '100px 76px minmax(0,1.25fr) minmax(0,1fr) minmax(0,1fr) 84px auto',
  gap: 14,
  alignItems: 'center',
};

const CHIP: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  height: 32,
  padding: '0 13px',
  borderRadius: 999,
  fontFamily: FONT_STACK,
  fontSize: 12.5,
  fontWeight: 500,
  cursor: 'pointer',
  whiteSpace: 'nowrap',
  transition: 'all 140ms ease',
  border: '1px solid ' + COLORS.line,
  background: 'transparent',
  color: COLORS.textMuted,
};

const CHIP_ON: CSSProperties = { ...CHIP, borderColor: COLORS.cyan, background: COLORS.raised, color: COLORS.text };

function AddFlightButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      height: 42,
      padding: '0 20px',
      borderRadius: 9,
      background: COLORS.orange,
      color: COLORS.textOnOrange,
      fontSize: 14,
      fontWeight: 700,
      textDecoration: 'none',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <Link to="/add" {...hover}>
      <span style={{ fontSize: 17, lineHeight: 1, marginTop: -2 }}>+</span>Add flight
    </Link>
  );
}

function FlightRow({ userFlight }: { userFlight: UserFlight }) {
  const hover = useHoverStyle(
    {
      ...GRID,
      padding: '14px 20px',
      borderBottom: '1px solid ' + COLORS.lineSoft,
      fontSize: 13.5,
      textDecoration: 'none',
      color: 'inherit',
    },
    { background: COLORS.raised },
  );
  return (
    <Link to={'/flights/' + userFlight.id} {...hover}>
      <span style={{ color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums' }}>{formatDate(userFlight.flight.flightDate)}</span>
      <span style={{ color: COLORS.text, fontWeight: 600, letterSpacing: '0.03em' }}>{userFlight.flight.flightNumber ?? '—'}</span>
      <span style={{ color: COLORS.text, fontWeight: 500 }}>{routeOf(userFlight)}</span>
      <span style={{ color: COLORS.textMuted }}>{userFlight.flight.airline.name}</span>
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

function matches(userFlight: UserFlight, query: string): boolean {
  const haystack = [
    userFlight.flight.flightNumber,
    userFlight.flight.airline.name,
    userFlight.flight.departureAirport.iata,
    userFlight.flight.departureAirport.city,
    userFlight.flight.departureAirport.name,
    userFlight.flight.arrivalAirport.iata,
    userFlight.flight.arrivalAirport.city,
    userFlight.flight.arrivalAirport.name,
    aircraftLabel(userFlight),
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  return haystack.includes(query.toLowerCase());
}

export function MyFlightsPage() {
  const [flights, setFlights] = useState<UserFlight[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('All');
  const [shown, setShown] = useState(PAGE_SIZE);

  useEffect(() => {
    let live = true;
    fetchMyFlights()
      .then((loaded) => live && setFlights(loaded))
      .catch(() => undefined)
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, []);

  const rows = useMemo(() => {
    let list = [...flights];
    if (filter === 'Past') list = list.filter((item) => !item.flight.upcoming);
    if (filter === 'Upcoming') list = list.filter((item) => item.flight.upcoming);
    if (filter === 'Cargo') list = list.filter((item) => item.flight.cargo);
    if (query.trim()) list = list.filter((item) => matches(item, query.trim()));

    if (filter === 'Longest') list.sort((a, b) => b.flight.distanceKm - a.flight.distanceKm);
    else if (filter === 'Oldest') list.sort((a, b) => a.flight.flightDate.localeCompare(b.flight.flightDate));
    else list.sort((a, b) => b.flight.flightDate.localeCompare(a.flight.flightDate));

    return list;
  }, [flights, filter, query]);

  const totalKm = flights.filter((item) => !item.flight.upcoming && !item.flight.canceled).reduce((sum, item) => sum + item.flight.distanceKm, 0);
  const upcomingCount = flights.filter((item) => item.flight.upcoming).length;

  if (loading) {
    return (
      <PageShell maxWidth={1240}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading your flights
        </div>
      </PageShell>
    );
  }

  return (
    <PageShell maxWidth={1240}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <h1 style={{ margin: 0, fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em' }}>My flights</h1>
          <span style={{ fontSize: 13.5, color: COLORS.textMuted }}>
            {flights.length === 0
              ? 'Nothing logged yet'
              : formatNumber(flights.length) +
                ' flights · ' +
                formatKm(totalKm) +
                (upcomingCount > 0 ? ' · ' + upcomingCount + ' upcoming' : '')}
          </span>
        </div>
        <AddFlightButton />
      </div>

      {flights.length > 0 && (
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 10,
            alignItems: 'center',
            padding: '14px 16px',
            background: COLORS.surfaceOverlay,
            border: '1px solid ' + COLORS.line,
            borderRadius: 12,
          }}
        >
          <input
            type="text"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setShown(PAGE_SIZE);
            }}
            placeholder="Search flight number, airport, airline"
            spellCheck={false}
            style={{
              flex: '1 1 250px',
              minWidth: 0,
              height: 40,
              padding: '0 13px',
              borderRadius: 8,
              background: COLORS.ground,
              border: '1px solid ' + COLORS.lineStrong,
              fontFamily: FONT_STACK,
              fontSize: 13.5,
              color: COLORS.text,
              outline: 'none',
            }}
          />
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {FILTERS.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => {
                  setFilter(item);
                  setShown(PAGE_SIZE);
                }}
                style={filter === item ? CHIP_ON : CHIP}
              >
                {item}
              </button>
            ))}
          </div>
        </div>
      )}

      {flights.length === 0 && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 16,
            padding: '72px 28px',
            background: 'rgba(10,44,57,0.7)',
            border: '1px dashed ' + COLORS.lineStrong,
            borderRadius: 14,
            textAlign: 'center',
          }}
        >
          <svg viewBox="0 0 120 60" width={130} height={65} fill="none" stroke={COLORS.lineStrong} strokeWidth={1.6} aria-hidden="true">
            <path d="M6 48 C36 14 84 14 114 48" strokeDasharray="4 5" />
            <circle cx={6} cy={48} r={3} fill={COLORS.lineStrong} stroke="none" />
            <circle cx={114} cy={48} r={3} fill={COLORS.lineStrong} stroke="none" />
          </svg>
          <div style={{ fontSize: 18, fontWeight: 600 }}>No flights yet</div>
          <div style={{ fontSize: 13.5, color: COLORS.textMuted, maxWidth: 320, lineHeight: 1.6 }}>
            Everything else in WTW is built from this list. Add the first one and the rest fills in.
          </div>
          <AddFlightButton />
        </div>
      )}

      {flights.length > 0 && rows.length === 0 && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 14,
            padding: '64px 28px',
            background: 'rgba(10,44,57,0.7)',
            border: '1px dashed ' + COLORS.lineStrong,
            borderRadius: 14,
            textAlign: 'center',
          }}
        >
          <div style={{ fontSize: 18, fontWeight: 600 }}>{query ? 'Nothing matches “' + query + '”' : 'Nothing matches that filter'}</div>
          <div style={{ fontSize: 13.5, color: COLORS.textMuted, maxWidth: 340, lineHeight: 1.6 }}>
            No flight in your log fits. Clearing the filters may help, or the flight may not be added yet.
          </div>
          <button
            type="button"
            onClick={() => {
              setQuery('');
              setFilter('All');
            }}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              height: 38,
              padding: '0 18px',
              borderRadius: 8,
              border: '1px solid ' + COLORS.cyan,
              background: 'transparent',
              color: COLORS.cyan,
              fontFamily: FONT_STACK,
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            Clear search and filters
          </button>
        </div>
      )}

      {rows.length > 0 && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            background: COLORS.surfaceOverlay,
            border: '1px solid ' + COLORS.line,
            borderRadius: 14,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              ...GRID,
              padding: '11px 20px',
              background: '#082834',
              borderBottom: '1px solid ' + COLORS.line,
              fontSize: 11,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
              color: COLORS.textDim,
            }}
          >
            <span>Date</span>
            <span>Flight</span>
            <span>Route</span>
            <span>Airline</span>
            <span>Aircraft</span>
            <span style={{ textAlign: 'right' }}>Distance</span>
            <span />
          </div>

          {rows.slice(0, shown).map((userFlight) => (
            <FlightRow key={userFlight.id} userFlight={userFlight} />
          ))}

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 14,
              padding: '14px 20px',
              fontSize: 13,
              color: COLORS.textMuted,
            }}
          >
            <span>
              Showing {Math.min(shown, rows.length)} of {rows.length}
            </span>
            {shown < rows.length && (
              <button
                type="button"
                onClick={() => setShown((count) => count + PAGE_SIZE)}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  height: 34,
                  padding: '0 16px',
                  borderRadius: 8,
                  border: '1px solid ' + COLORS.lineStrong,
                  background: 'transparent',
                  color: COLORS.textMuted,
                  fontFamily: FONT_STACK,
                  fontSize: 13,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                }}
              >
                Load more
              </button>
            )}
          </div>
        </div>
      )}
    </PageShell>
  );
}
