import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { COLORS, FONT_STACK } from '../design/tokens';
import { PageShell } from '../components/PageShell';
import { Spinner } from '../components/Spinner';
import { fetchMyFlights } from '../api/tailwind';
import type { UserFlight } from '../api/types';
import { aircraftLabel, routeOf } from '../components/FlightFlags';
import { earthLaps, formatDate, formatKm, formatNumber } from '../format';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

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
  border: '1px solid ' + COLORS.line,
  background: 'transparent',
  color: COLORS.textMuted,
};

const CHIP_ON: CSSProperties = { ...CHIP, borderColor: COLORS.cyan, background: COLORS.raised, color: COLORS.text };

const CELL: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  padding: '22px 24px',
  borderRight: '1px solid ' + COLORS.line,
  borderBottom: '1px solid ' + COLORS.line,
};

const EYEBROW: CSSProperties = { fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim };

function minutesOf(flight: UserFlight): number {
  const start = flight.flight.departureActualUtc ?? flight.flight.departureScheduledUtc;
  const end = flight.flight.arrivalActualUtc ?? flight.flight.arrivalScheduledUtc;
  if (!start || !end) return 0;
  const minutes = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 60000);
  return minutes > 0 ? minutes : 0;
}

/**
 * The yearly recap. The job that mails one out each January is a later feature, but the page itself is
 * built from the log as it stands, so the numbers are real.
 */
export function RecapPage() {
  const [flights, setFlights] = useState<UserFlight[]>([]);
  const [loading, setLoading] = useState(true);
  const [year, setYear] = useState<number | null>(null);

  useEffect(() => {
    let live = true;
    fetchMyFlights()
      .then((loaded) => {
        if (!live) return;
        const flown = loaded.filter((flight) => !flight.flight.upcoming);
        setFlights(flown);
        const years = [...new Set(flown.map((flight) => Number(flight.flight.flightDate.slice(0, 4))))].sort((a, b) => b - a);
        setYear(years[0] ?? new Date().getFullYear());
      })
      .catch(() => undefined)
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, []);

  const years = useMemo(
    () => [...new Set(flights.map((flight) => Number(flight.flight.flightDate.slice(0, 4))))].sort((a, b) => b - a),
    [flights],
  );

  const inYear = useMemo(
    () => flights.filter((flight) => Number(flight.flight.flightDate.slice(0, 4)) === year),
    [flights, year],
  );

  const before = useMemo(
    () => flights.filter((flight) => Number(flight.flight.flightDate.slice(0, 4)) < (year ?? 0)),
    [flights, year],
  );

  if (loading || year === null) {
    return (
      <PageShell maxWidth={1000}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Building your recap
        </div>
      </PageShell>
    );
  }

  const distance = inYear.reduce((sum, flight) => sum + flight.flight.distanceKm, 0);
  const minutes = inYear.reduce((sum, flight) => sum + minutesOf(flight), 0);

  const countriesThisYear = new Set<string>();
  const countriesBefore = new Set<string>();
  const familiesThisYear = new Set<string>();
  const familiesBefore = new Set<string>();
  for (const flight of inYear) {
    countriesThisYear.add(flight.flight.departureAirport.countryCode);
    countriesThisYear.add(flight.flight.arrivalAirport.countryCode);
    if (flight.flight.aircraft.family) familiesThisYear.add(flight.flight.aircraft.family);
  }
  for (const flight of before) {
    countriesBefore.add(flight.flight.departureAirport.countryCode);
    countriesBefore.add(flight.flight.arrivalAirport.countryCode);
    if (flight.flight.aircraft.family) familiesBefore.add(flight.flight.aircraft.family);
  }
  const newCountries = [...countriesThisYear].filter((code) => !countriesBefore.has(code));
  const newAircraft = [...familiesThisYear].filter((family) => !familiesBefore.has(family));

  const monthCounts = MONTHS.map((_, index) => inYear.filter((flight) => Number(flight.flight.flightDate.slice(5, 7)) === index + 1).length);
  const maxMonth = Math.max(1, ...monthCounts);
  const busiest = monthCounts.indexOf(maxMonth);

  const longest = [...inYear].sort((a, b) => b.flight.distanceKm - a.flight.distanceKm)[0];
  const first = [...inYear].sort((a, b) => a.flight.flightDate.localeCompare(b.flight.flightDate))[0];
  const airlineCounts = new Map<string, number>();
  for (const flight of inYear) airlineCounts.set(flight.flight.airline.name, (airlineCounts.get(flight.flight.airline.name) ?? 0) + 1);
  const topAirline = [...airlineCounts.entries()].sort((a, b) => b[1] - a[1])[0];

  const hero = [
    { label: 'Distance', value: formatKm(distance), note: earthLaps(distance) + ' times around the Earth', color: COLORS.text },
    { label: 'Flights', value: formatNumber(inYear.length), note: 'logged in ' + year, color: COLORS.text },
    {
      label: 'Time in air',
      value: minutes > 0 ? Math.round(minutes / 60) + ' h' : '—',
      note: minutes > 0 ? 'from the flights with times' : 'no times recorded',
      color: COLORS.cyan,
    },
    { label: 'Countries', value: formatNumber(countriesThisYear.size), note: newCountries.length + ' of them new', color: COLORS.cyan },
  ];

  const moments = [
    longest && {
      label: 'Longest flight',
      value: routeOf(longest),
      note: formatKm(longest.flight.distanceKm) + ' · ' + formatDate(longest.flight.flightDate),
    },
    first && { label: 'The year started with', value: routeOf(first), note: formatDate(first.flight.flightDate) },
    topAirline && { label: 'Most flown airline', value: topAirline[0], note: topAirline[1] + (topAirline[1] === 1 ? ' flight' : ' flights') },
    longest && {
      label: 'Aircraft of the year',
      value: aircraftLabel(longest),
      note: 'on your longest flight of ' + year,
    },
  ].filter(Boolean) as { label: string; value: string; note: string }[];

  return (
    <PageShell maxWidth={1000}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 7,
            padding: '4px 11px',
            borderRadius: 999,
            background: COLORS.laterBg,
            border: '1px solid ' + COLORS.laterBorder,
            color: COLORS.laterText,
            fontSize: 10.5,
            fontWeight: 700,
            letterSpacing: '0.1em',
          }}
        >
          LATER FEATURE
        </div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {years.map((item) => (
            <button key={item} type="button" onClick={() => setYear(item)} style={year === item ? CHIP_ON : CHIP}>
              {item}
            </button>
          ))}
        </div>
      </div>

      <div style={{ background: 'rgba(10,44,57,0.9)', border: '1px solid ' + COLORS.lineStrong, borderRadius: 18, overflow: 'hidden' }}>
        <div style={{ position: 'relative', padding: '46px 40px 40px', borderBottom: '1px solid ' + COLORS.line, overflow: 'hidden' }}>
          <svg
            style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.5 }}
            viewBox="0 0 920 260"
            preserveAspectRatio="xMidYMid slice"
            aria-hidden="true"
          >
            <g fill="none" stroke="#0F3D4C" strokeWidth={1}>
              <path d="M-40 210 C160 90 560 66 960 150" />
              <path d="M-40 240 C180 140 600 116 960 60" />
            </g>
          </svg>
          <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ fontSize: 11, letterSpacing: '0.22em', textTransform: 'uppercase', color: COLORS.orange }}>Your year in the air</div>
            <h1 style={{ margin: 0, fontSize: 'clamp(38px, 6vw, 62px)', fontWeight: 600, letterSpacing: '-0.03em', lineHeight: 1 }}>{year}</h1>
            <p style={{ margin: 0, fontSize: 16, lineHeight: 1.6, color: COLORS.bodyOnCard, maxWidth: 520, textWrap: 'pretty' }}>
              {inYear.length === 0
                ? 'Nothing logged in ' + year + ' yet.'
                : formatNumber(inYear.length) +
                  (inYear.length === 1 ? ' flight, ' : ' flights, ') +
                  formatKm(distance) +
                  ' and ' +
                  countriesThisYear.size +
                  (countriesThisYear.size === 1 ? ' country' : ' countries') +
                  (busiest >= 0 && maxMonth > 0 ? ', busiest in ' + MONTHS[busiest] + '.' : '.')}
            </p>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
          {hero.map((item) => (
            <div key={item.label} style={CELL}>
              <span style={EYEBROW}>{item.label}</span>
              <span style={{ fontSize: 27, fontWeight: 600, fontVariantNumeric: 'tabular-nums', lineHeight: 1.05, color: item.color }}>
                {item.value}
              </span>
              <span style={{ fontSize: 12, color: COLORS.textMuted, lineHeight: 1.45 }}>{item.note}</span>
            </div>
          ))}
        </div>

        <div style={{ padding: '28px 24px', borderBottom: '1px solid ' + COLORS.line }}>
          <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 12, marginBottom: 20, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 16, fontWeight: 600 }}>Month by month</span>
            <span style={{ fontSize: 12, color: COLORS.textDim }}>
              {maxMonth > 0 ? MONTHS[busiest] + ' was the busiest with ' + maxMonth : 'nothing logged this year'}
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 7, height: 150 }}>
            {monthCounts.map((count, index) => (
              <div
                key={MONTHS[index]}
                style={{
                  flex: '1 1 0',
                  minWidth: 0,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: 7,
                  height: '100%',
                  justifyContent: 'flex-end',
                }}
              >
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    color: count === maxMonth && count > 0 ? '#FFB067' : COLORS.textMuted,
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  {count}
                </span>
                <span
                  style={{
                    width: '100%',
                    borderRadius: '3px 3px 0 0',
                    background: count === maxMonth && count > 0 ? COLORS.orange : '#1B6D84',
                    height: Math.round((count / maxMonth) * 100) + '%',
                  }}
                />
                <span style={{ fontSize: 10, color: COLORS.textDim }}>{MONTHS[index]}</span>
              </div>
            ))}
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))' }}>
          {moments.map((moment) => (
            <div key={moment.label} style={{ ...CELL, gap: 9 }}>
              <span style={EYEBROW}>{moment.label}</span>
              <span style={{ fontSize: 19, fontWeight: 600, letterSpacing: '-0.01em', lineHeight: 1.25 }}>{moment.value}</span>
              <span style={{ fontSize: 12.5, color: COLORS.textMuted, lineHeight: 1.5 }}>{moment.note}</span>
            </div>
          ))}
        </div>

        <div style={{ padding: '26px 24px', display: 'flex', flexDirection: 'column', gap: 16 }}>
          <span style={{ fontSize: 16, fontWeight: 600 }}>New this year</span>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
              <span style={EYEBROW}>Countries you had never been to</span>
              <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap' }}>
                {newCountries.map((code) => (
                  <span
                    key={code}
                    style={{
                      padding: '6px 12px',
                      borderRadius: 999,
                      background: COLORS.raised,
                      border: '1px solid ' + COLORS.cyan,
                      color: COLORS.cyanBright,
                      fontSize: 12.5,
                      fontWeight: 500,
                    }}
                  >
                    {code}
                  </span>
                ))}
                {newCountries.length === 0 && <span style={{ fontSize: 13, color: COLORS.textMuted }}>No first visits this year.</span>}
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 9, paddingTop: 14, borderTop: '1px solid ' + COLORS.lineSoft }}>
              <span style={EYEBROW}>Aircraft you added to the collection</span>
              <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap' }}>
                {newAircraft.map((family) => (
                  <span
                    key={family}
                    style={{
                      padding: '6px 12px',
                      borderRadius: 999,
                      background: '#2E1D0C',
                      border: '1px solid #7A4A16',
                      color: '#FFB067',
                      fontSize: 12.5,
                      fontWeight: 500,
                    }}
                  >
                    {family}
                  </span>
                ))}
                {newAircraft.length === 0 && <span style={{ fontSize: 13, color: COLORS.textMuted }}>No new families this year.</span>}
              </div>
            </div>
          </div>
        </div>
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          flexWrap: 'wrap',
          padding: '20px 22px',
          background: COLORS.surfaceOverlay,
          border: '1px solid ' + COLORS.line,
          borderRadius: 14,
        }}
      >
        <span style={{ fontSize: 13, lineHeight: 1.6, color: COLORS.textMuted, maxWidth: 560 }}>
          Built from the flights you have logged, private ones included — a recap is only ever shown to you. The January email and
          the sharing card are a later feature, so this page is the whole of it for now.
        </span>
      </div>
    </PageShell>
  );
}
