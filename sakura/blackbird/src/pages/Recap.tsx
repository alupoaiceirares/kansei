import { useEffect, useState, type CSSProperties } from 'react';
import { useSearchParams } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { PageShell } from '../components/PageShell';
import { Spinner } from '../components/Spinner';
import { Banner } from '../components/Banner';
import { fetchRecap, fetchRecapYears } from '../api/tailwind';
import type { YearlyRecap } from '../api/types';
import { formatDate, formatKm, formatNumber } from '../format';

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

/**
 * The yearly recap. The numbers come from tailwind, the same ones the January email carries, and upcoming or
 * canceled flights never count.
 */
export function RecapPage() {
  const [params, setParams] = useSearchParams();
  const [years, setYears] = useState<number[] | null>(null);
  const [recap, setRecap] = useState<YearlyRecap | null>(null);
  const [failed, setFailed] = useState(false);

  const requested = Number(params.get('year'));
  const year = years === null ? null : years.includes(requested) ? requested : (years[0] ?? new Date().getFullYear());

  useEffect(() => {
    fetchRecapYears()
      .then(setYears)
      .catch(() => {
        setYears([]);
        setFailed(true);
      });
  }, []);

  useEffect(() => {
    if (year === null) return;
    let live = true;
    setRecap(null);
    fetchRecap(year)
      .then((loaded) => live && setRecap(loaded))
      .catch(() => live && setFailed(true));
    return () => {
      live = false;
    };
  }, [year]);

  const setYear = (next: number) => setParams({ year: String(next) }, { replace: true });

  if (failed) {
    return (
      <PageShell maxWidth={1000}>
        <Banner tone="error" title="The recap could not be loaded">
          Try again in a moment.
        </Banner>
      </PageShell>
    );
  }

  if (year === null || recap === null || years === null) {
    return (
      <PageShell maxWidth={1000}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Building your recap
        </div>
      </PageShell>
    );
  }

  const monthCounts = recap.monthCounts;
  const maxMonth = Math.max(1, ...monthCounts);
  const busiest = recap.busiestMonth === null ? -1 : recap.busiestMonth - 1;
  const minutes = recap.timeInAirMinutes;
  const longest = recap.longestFlight;
  const first = recap.firstFlight;

  const hero = [
    {
      label: 'Distance',
      value: formatKm(recap.distanceKm),
      note: recap.earthLaps + ' times around the Earth, ' + recap.moonTrips + ' of the way to the Moon',
      color: COLORS.text,
    },
    { label: 'Flights', value: formatNumber(recap.flightCount), note: 'flown in ' + year, color: COLORS.text },
    {
      label: 'Time in air',
      value: minutes > 0 ? Math.round(minutes / 60) + ' h' : '—',
      note: minutes > 0 ? 'from the flights with times' : 'no times recorded',
      color: COLORS.cyan,
    },
    { label: 'Countries', value: formatNumber(recap.countryCount), note: recap.newCountries.length + ' of them new', color: COLORS.cyan },
  ];

  const moments = [
    longest && {
      label: 'Longest flight',
      value: longest.route,
      note: formatKm(longest.distanceKm) + ' · ' + formatDate(longest.date),
    },
    first && { label: 'The year started with', value: first.route, note: formatDate(first.date) },
    recap.topAirline && {
      label: 'Most flown airline',
      value: recap.topAirline.name,
      note: recap.topAirline.count + (recap.topAirline.count === 1 ? ' flight' : ' flights'),
    },
    longest?.aircraft && { label: 'Aircraft of the year', value: longest.aircraft, note: 'on your longest flight of ' + year },
  ].filter(Boolean) as { label: string; value: string; note: string }[];

  const newCountries = recap.newCountries;
  const newAircraft = recap.newAircraftFamilies;

  return (
    <PageShell maxWidth={1000}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
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
              {recap.flightCount === 0
                ? 'Nothing logged in ' + year + ' yet.'
                : formatNumber(recap.flightCount) +
                  (recap.flightCount === 1 ? ' flight, ' : ' flights, ') +
                  formatKm(recap.distanceKm) +
                  ' and ' +
                  recap.countryCount +
                  (recap.countryCount === 1 ? ' country' : ' countries') +
                  (busiest >= 0 ? ', busiest in ' + MONTHS[busiest] + '.' : '.')}
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
              {busiest >= 0 ? MONTHS[busiest] + ' was the busiest with ' + maxMonth : 'nothing logged this year'}
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
                {newCountries.map((country) => (
                  <span
                    key={country.code}
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
                    {country.name}
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
          Built from the flights you have flown, private ones included, and only ever shown to you. Early each January the
          previous year comes by email too, you can turn that off on your profile.
        </span>
      </div>
    </PageShell>
  );
}
