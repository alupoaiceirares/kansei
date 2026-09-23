import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { COLORS, FONT_STACK } from '../design/tokens';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import { AircraftPhoto } from '../components/AircraftPhoto';
import { fetchFriendTotals, fetchStatsProfile, type FriendTotals, type StatsProfile } from '../api/profile';
import { fetchFriends, fetchMyFlights } from '../api/tailwind';
import type { Friend, UserFlight } from '../api/types';
import { useSession } from '../session';
import { earthLaps, formatDate, formatKm, formatMinutes, formatNumber, initialsOf } from '../format';

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

const SECTION: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  flex: '1 1 140px',
  height: 40,
  padding: '0 18px',
  borderRadius: 8,
  fontFamily: FONT_STACK,
  fontSize: 14,
  fontWeight: 600,
  cursor: 'pointer',
  whiteSpace: 'nowrap',
  transition: 'all 140ms ease',
  border: 'none',
  background: 'transparent',
  color: COLORS.textMuted,
};

const SECTION_ON: CSSProperties = { ...SECTION, background: COLORS.raised, color: COLORS.text, boxShadow: 'inset 0 -2px 0 ' + COLORS.cyan };

const CARD: CSSProperties = {
  background: COLORS.surfaceOverlay,
  border: '1px solid ' + COLORS.line,
  borderRadius: 14,
  overflow: 'hidden',
};

const CARD_HEAD: CSSProperties = { padding: '15px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 };
const EYEBROW: CSSProperties = { fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim };

type Section = 'Stats' | 'Collections' | 'Records';
type Period = 'All time' | 'This year' | 'Custom range';
type CollectionFilter = 'All families' | 'Flown only';

const CABIN_LABELS = [
  { key: 'ECONOMY', label: 'Economy', color: '#1B6D84' },
  { key: 'PREMIUM_ECONOMY', label: 'Premium', color: '#2BB3D9' },
  { key: 'BUSINESS', label: 'Business', color: '#7FE3FF' },
  { key: 'FIRST', label: 'First', color: '#FF7A18' },
] as const;

const REASON_LABELS = [
  { key: 'LEISURE', label: 'Leisure', color: '#1B6D84' },
  { key: 'BUSINESS', label: 'Business', color: '#2BB3D9' },
] as const;

/** A gap reads in months once it is past one, days below that. */
function gapLabel(days: number): string {
  if (days < 60) return days + ' days';
  return Math.round(days / 30.44) + ' months';
}

/** Service counts against the design's labels, as a share of the flights that carry a value at all. */
function split(counts: { name: string; count: number }[], labels: readonly { key: string; label: string; color: string }[]) {
  const total = counts.reduce((sum, item) => sum + item.count, 0);
  return labels
    .map((label) => {
      const count = counts.find((item) => item.name === label.key)?.count ?? 0;
      return { ...label, count, pct: total ? Math.round((count / total) * 100) : 0 };
    })
    .filter((item) => item.count > 0);
}

export function StatsPage() {
  const { me, displayName } = useSession();
  const [section, setSection] = useState<Section>('Stats');
  const [period, setPeriod] = useState<Period>('All time');
  const [collectionFilter, setCollectionFilter] = useState<CollectionFilter>('All families');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const [profile, setProfile] = useState<StatsProfile | null>(null);
  const [flights, setFlights] = useState<UserFlight[]>([]);
  const [friends, setFriends] = useState<Friend[]>([]);
  const [friendTotals, setFriendTotals] = useState<FriendTotals[]>([]);
  const [loading, setLoading] = useState(true);

  const range = useMemo(() => {
    if (period === 'This year') return { from: new Date().getFullYear() + '-01-01' };
    if (period === 'Custom range' && (from || to)) return { from: from || undefined, to: to || undefined };
    return undefined;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period, from, to]);

  useEffect(() => {
    let live = true;
    setLoading(true);
    fetchStatsProfile(range)
      .then((loaded) => live && setProfile(loaded))
      .catch(() => undefined)
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, [range]);

  useEffect(() => {
    let live = true;
    Promise.all([fetchMyFlights().catch(() => []), fetchFriends().catch(() => [])]).then(([loadedFlights, loadedFriends]) => {
      if (!live) return;
      setFlights(loadedFlights);
      setFriends(loadedFriends);
      if (loadedFriends.length > 0) {
        fetchFriendTotals(loadedFriends.map((friend) => friend.userId))
          .then((totals) => live && setFriendTotals(totals))
          .catch(() => undefined);
      }
    });
    return () => {
      live = false;
    };
  }, []);

  const flown = useMemo(() => flights.filter((item) => !item.flight.upcoming && !item.flight.canceled), [flights]);

  // Cabin, reason and per-year counts come from the service, the labels and colours are the design's.
  const years = useMemo(
    () => (profile?.breakdowns.flightsPerYear ?? []).map((year) => ({ year: Number(year.label), count: year.count })),
    [profile],
  );

  const cabinSplit = useMemo(() => split(profile?.breakdowns.cabinClasses ?? [], CABIN_LABELS), [profile]);
  const reasonSplit = useMemo(() => split(profile?.breakdowns.reasons ?? [], REASON_LABELS), [profile]);

  /**
   * The catalog is every airliner family, the collection is what the user has flown of it. A family they
   * have never been on shows its variants as what is still missing.
   */
  const collection = useMemo(() => {
    if (!profile) return [];
    const flownByFamily = new Map(profile.aircraft.map((family) => [family.family, family]));
    return profile.aircraftCatalog
      .map((entry) => {
        const flownFamily = flownByFamily.get(entry.family);
        return {
          family: entry.family,
          flown: entry.flightCount > 0,
          flightCount: entry.flightCount,
          detail: flownFamily
            ? flownFamily.variants.map((variant) => variant.name + ' × ' + variant.flightCount).join(', ')
            : entry.variantCount + (entry.variantCount === 1 ? ' variant' : ' variants') +
              (entry.manufacturer ? ' · ' + entry.manufacturer : ''),
        };
      })
      .filter((entry) => collectionFilter === 'All families' || entry.flown)
      .sort((a, b) => b.flightCount - a.flightCount || a.family.localeCompare(b.family));
  }, [profile, collectionFilter]);

  const routeBoard = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of flown) {
      const key =
        (item.flight.departureAirport.iata ?? item.flight.departureAirport.icao) +
        ' → ' +
        (item.flight.arrivalAirport.iata ?? item.flight.arrivalAirport.icao);
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([name, count]) => ({ name, value: count + (count === 1 ? ' time' : ' times') }));
  }, [flown]);

  if (loading || !profile) {
    return (
      <PageShell maxWidth={1240}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Counting your log
        </div>
      </PageShell>
    );
  }

  const stats = profile.stats;
  const missingTimes = stats.flightCount - stats.flightsWithDuration;
  const maxYear = Math.max(1, ...years.map((year) => year.count));

  const bigStats = [
    { label: 'Distance flown', value: formatKm(stats.distanceKm), note: earthLaps(stats.distanceKm) + ' times around the Earth', color: COLORS.text },
    {
      label: 'Time in air',
      value: formatMinutes(stats.timeInAirMinutes),
      note: Math.floor(stats.timeInAirMinutes / 1440) + ' days and ' + Math.floor((stats.timeInAirMinutes % 1440) / 60) + ' hours',
      color: COLORS.text,
    },
    { label: 'Flights', value: formatNumber(stats.flightCount), note: 'upcoming flights are not counted', color: COLORS.text },
    {
      label: 'Countries visited',
      value: formatNumber(stats.countryCount),
      note: 'plus ' + profile.countries.passedThrough.length + ' passed through',
      color: COLORS.cyan,
    },
    { label: 'Airports', value: formatNumber(stats.airportCount), note: 'used at least once', color: COLORS.cyan },
    { label: 'Airlines', value: formatNumber(stats.airlineCount), note: formatNumber(stats.aircraftTypeCount) + ' aircraft variants', color: COLORS.cyan },
  ];

  const records = [
    profile.records.longestFlight && {
      label: 'Longest flight',
      value: (profile.records.longestFlight.departureIata ?? '?') + ' → ' + (profile.records.longestFlight.arrivalIata ?? '?'),
      note:
        formatKm(profile.records.longestFlight.distanceKm) +
        ' · ' +
        (profile.records.longestFlight.aircraftName ?? 'aircraft unknown') +
        ' · ' +
        formatDate(profile.records.longestFlight.date),
    },
    profile.records.shortestFlight && {
      label: 'Shortest flight',
      value: (profile.records.shortestFlight.departureIata ?? '?') + ' → ' + (profile.records.shortestFlight.arrivalIata ?? '?'),
      note: formatKm(profile.records.shortestFlight.distanceKm) + ' · ' + formatDate(profile.records.shortestFlight.date),
    },
    profile.records.firstFlight && {
      label: 'First flight',
      value: (profile.records.firstFlight.departureIata ?? '?') + ' → ' + (profile.records.firstFlight.arrivalIata ?? '?'),
      note: formatDate(profile.records.firstFlight.date) + ' · ' + profile.records.firstFlight.airlineName,
    },
    profile.records.mostFlownRoute && {
      label: 'Most flown route',
      value: (profile.records.mostFlownRoute.departureIata ?? '?') + ' → ' + (profile.records.mostFlownRoute.arrivalIata ?? '?'),
      note: profile.records.mostFlownRoute.flightCount + ' times · ' + formatKm(profile.records.mostFlownRoute.distanceKm),
    },
    profile.records.mostFlownAirline && {
      label: 'Most flown airline',
      value: profile.records.mostFlownAirline.name,
      note: profile.records.mostFlownAirline.count + ' flights',
    },
    profile.records.mostFlownAircraftFamily && {
      label: 'Most flown aircraft',
      value: profile.records.mostFlownAircraftFamily.name,
      note: profile.records.mostFlownAircraftFamily.count + ' flights',
    },
    profile.records.busiestMonth && {
      label: 'Busiest month',
      value: profile.records.busiestMonth.label,
      note: profile.records.busiestMonth.count + ' flights, ' + formatKm(profile.records.busiestMonth.distanceKm),
    },
    profile.records.biggestYear && {
      label: 'Biggest year',
      value: profile.records.biggestYear.label,
      note: profile.records.biggestYear.count + ' flights, ' + formatKm(profile.records.biggestYear.distanceKm),
    },
    profile.records.furthestPoint && {
      label: 'Furthest point',
      value: profile.records.furthestPoint.city ?? profile.records.furthestPoint.name,
      note:
        formatKm(profile.records.furthestPoint.distanceFromHomeKm) +
        ' from ' +
        (profile.records.furthestPoint.homeIata ?? 'home') +
        ' · ' +
        formatDate(profile.records.furthestPoint.date),
    },
    profile.records.longestGap && {
      label: 'Longest gap',
      value: gapLabel(profile.records.longestGap.days),
      note: 'between ' + formatDate(profile.records.longestGap.from) + ' and ' + formatDate(profile.records.longestGap.to),
    },
    profile.records.mostAircraftInAJourney && {
      label: 'Most aircraft in a journey',
      value: profile.records.mostAircraftInAJourney.title,
      note:
        profile.records.mostAircraftInAJourney.aircraftCount +
        ' different types across ' +
        profile.records.mostAircraftInAJourney.flightCount +
        ' legs',
    },
    profile.records.highestCabin && {
      label: 'Highest cabin flown',
      value: CABIN_LABELS.find((cabin) => cabin.key === profile.records.highestCabin?.cabinClass)?.label ?? profile.records.highestCabin.cabinClass,
      note:
        profile.records.highestCabin.flightCount +
        (profile.records.highestCabin.flightCount === 1 ? ' flight, first on ' : ' flights, first on ') +
        formatDate(profile.records.highestCabin.firstFlight),
    },
  ].filter(Boolean) as { label: string; value: string; note: string }[];

  const board = [
    ...friends.map((friend) => {
      const totals = friendTotals.find((item) => item.userId === friend.userId);
      return {
        id: friend.userId,
        name: friend.username ?? 'Unknown user',
        km: totals?.distanceKm ?? 0,
        flights: totals?.flightCount ?? 0,
        countries: totals?.countryCount ?? 0,
        me: false,
      };
    }),
    { id: me?.userId ?? 'me', name: 'You', km: stats.distanceKm, flights: stats.flightCount, countries: stats.countryCount, me: true },
  ].sort((a, b) => b.km - a.km);

  return (
    <PageShell maxWidth={1240}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxWidth: 620 }}>
          <h1 style={{ margin: 0, fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em' }}>Stats</h1>
          <p style={{ margin: 0, fontSize: 14, lineHeight: 1.6, color: COLORS.textMuted, textWrap: 'pretty' }}>
            Everything counted from your log. Upcoming flights are excluded until they have happened.
          </p>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
          <span style={EYEBROW}>Period</span>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
            {(['All time', 'This year', 'Custom range'] as Period[]).map((item) => (
              <button key={item} type="button" onClick={() => setPeriod(item)} style={period === item ? CHIP_ON : CHIP}>
                {item}
              </button>
            ))}
            {period === 'Custom range' && (
              <span
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 7,
                  padding: '5px 9px',
                  borderRadius: 999,
                  background: COLORS.ground,
                  border: '1px solid ' + COLORS.orange,
                }}
              >
                <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} style={dateStyle} />
                <span style={{ fontSize: 12, color: COLORS.textDim }}>to</span>
                <input type="date" value={to} onChange={(event) => setTo(event.target.value)} style={dateStyle} />
              </span>
            )}
          </div>
        </div>
      </div>

      <div
        style={{
          display: 'flex',
          gap: 4,
          flexWrap: 'wrap',
          padding: 5,
          background: COLORS.surfaceOverlay,
          border: '1px solid ' + COLORS.line,
          borderRadius: 11,
        }}
      >
        {(['Stats', 'Collections', 'Records'] as Section[]).map((item) => (
          <button key={item} type="button" onClick={() => setSection(item)} style={section === item ? SECTION_ON : SECTION}>
            {item}
          </button>
        ))}
      </div>

      {section === 'Stats' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 16 }}>
            {bigStats.map((stat) => (
              <div
                key={stat.label}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 6,
                  padding: '19px 21px',
                  background: COLORS.surfaceOverlay,
                  border: '1px solid ' + COLORS.line,
                  borderRadius: 13,
                }}
              >
                <span style={EYEBROW}>{stat.label}</span>
                <span style={{ fontSize: 27, fontWeight: 600, fontVariantNumeric: 'tabular-nums', lineHeight: 1.05, color: stat.color }}>
                  {stat.value}
                </span>
                <span style={{ fontSize: 12, color: COLORS.textMuted, lineHeight: 1.45 }}>{stat.note}</span>
              </div>
            ))}
          </div>

          {missingTimes > 0 && (
            <Banner tone="warning" title="Time in air is partial">
              {stats.flightsWithDuration} of your {stats.flightCount} flights have usable times. The other {missingTimes} were
              added without them, so they count towards distance but not hours.
            </Banner>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.4fr) minmax(0,1fr)', gap: 20, alignItems: 'start' }}>
            <div style={CARD}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'baseline',
                  justifyContent: 'space-between',
                  gap: 12,
                  padding: '16px 20px',
                  borderBottom: '1px solid ' + COLORS.line,
                }}
              >
                <span style={{ fontSize: 16, fontWeight: 600 }}>Flights per year</span>
                <span style={{ fontSize: 12, color: COLORS.textDim }}>
                  {formatNumber(stats.flightCount)} flights{years.length > 0 ? ' since ' + years[0].year : ''}
                </span>
              </div>
              <div style={{ padding: '24px 20px 18px' }}>
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: 10, height: 180 }}>
                  {years.length === 0 && <span style={{ fontSize: 13, color: COLORS.textMuted }}>Nothing counted yet.</span>}
                  {years.map((year) => (
                    <div
                      key={year.year}
                      style={{
                        flex: '1 1 0',
                        minWidth: 0,
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: 8,
                        height: '100%',
                        justifyContent: 'flex-end',
                      }}
                    >
                      <span
                        style={{
                          fontSize: 12,
                          fontWeight: 600,
                          color: year.count === maxYear ? '#FFB067' : COLORS.textMuted,
                          fontVariantNumeric: 'tabular-nums',
                        }}
                      >
                        {year.count}
                      </span>
                      <span
                        style={{
                          width: '100%',
                          borderRadius: '4px 4px 0 0',
                          background: year.count === maxYear ? COLORS.orange : '#1B6D84',
                          height: Math.round((year.count / maxYear) * 100) + '%',
                        }}
                      />
                      <span style={{ fontSize: 11, color: COLORS.textDim, fontVariantNumeric: 'tabular-nums' }}>{year.year}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div style={CARD}>
              <div style={CARD_HEAD}>Cabin and reason</div>
              <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 18 }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <span style={EYEBROW}>Cabin</span>
                  {cabinSplit.length === 0 && <span style={{ fontSize: 13, color: COLORS.textMuted }}>No cabin recorded yet.</span>}
                  {cabinSplit.map((cabin) => (
                    <SplitRow key={cabin.key} label={cabin.label} count={cabin.count} pct={cabin.pct} colour={cabin.color} />
                  ))}
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10, paddingTop: 16, borderTop: '1px solid ' + COLORS.lineSoft }}>
                  <span style={EYEBROW}>Reason</span>
                  {reasonSplit.length === 0 && <span style={{ fontSize: 13, color: COLORS.textMuted }}>No reason recorded yet.</span>}
                  {reasonSplit.map((reason) => (
                    <SplitRow key={reason.key} label={reason.label} count={reason.count} pct={reason.pct} colour={reason.color} />
                  ))}
                </div>
              </div>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(290px, 1fr))', gap: 20 }}>
            <Leaderboard title="Most flown routes" rows={routeBoard} />
            <Leaderboard
              title="Most used airports"
              rows={[...profile.airports]
                .sort((a, b) => b.timesUsed - a.timesUsed)
                .slice(0, 5)
                .map((airport) => ({
                  name: (airport.iata ?? '') + ' ' + (airport.city ?? airport.name),
                  value: airport.timesUsed + ' flights',
                }))}
            />
            <Leaderboard
              title="Most flown airlines"
              rows={[...profile.airlines]
                .sort((a, b) => b.flightCount - a.flightCount)
                .slice(0, 5)
                .map((airline) => ({ name: airline.name, value: airline.flightCount + ' flights' }))}
            />
          </div>
        </div>
      )}

      {section === 'Collections' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              <span style={{ fontSize: 20, fontWeight: 600 }}>Aircraft you have flown</span>
              <span style={{ fontSize: 13, color: COLORS.textMuted }}>
                {stats.aircraftFamilyCount} families, {stats.aircraftTypeCount} variants, out of{' '}
                {profile.aircraftCatalog.length} airliner families. One card per family, the variants are listed underneath.
              </span>
            </div>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {(['All families', 'Flown only'] as CollectionFilter[]).map((item) => (
                <button key={item} type="button" onClick={() => setCollectionFilter(item)} style={collectionFilter === item ? CHIP_ON : CHIP}>
                  {item}
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(230px, 1fr))', gap: 16 }}>
            {collection.map((family) => (
              <div
                key={family.family}
                style={{ ...CARD, display: 'flex', flexDirection: 'column', borderColor: family.flown ? COLORS.line : COLORS.lineSoft }}
              >
                <div style={{ position: 'relative', borderBottom: '1px solid ' + COLORS.line }}>
                  <div style={{ opacity: family.flown ? 1 : 0.35 }}>
                    <AircraftPhoto family={family.family} width={230} height={110} />
                  </div>
                  <span style={{ position: 'absolute', top: 10, right: 11 }}>
                    <span
                      style={{
                        padding: '3px 9px',
                        borderRadius: 999,
                        background: family.flown ? 'rgba(18,63,44,0.9)' : 'rgba(14,54,68,0.9)',
                        border: '1px solid ' + (family.flown ? '#2C6B4C' : COLORS.lineStrong),
                        color: family.flown ? '#7FE0AE' : COLORS.textMuted,
                        fontSize: 10,
                        fontWeight: 600,
                        letterSpacing: '0.07em',
                      }}
                    >
                      {family.flown ? 'FLOWN' : 'LOCKED'}
                    </span>
                  </span>
                </div>
                <div style={{ padding: '15px 17px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10 }}>
                    <span style={{ fontSize: 17, fontWeight: 600, color: family.flown ? COLORS.text : COLORS.bodyOnCard }}>
                      {family.family}
                    </span>
                    <span style={{ fontSize: 12.5, color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums' }}>
                      {family.flown ? family.flightCount + (family.flightCount === 1 ? ' flight' : ' flights') : 'Not yet'}
                    </span>
                  </div>
                  <div style={{ fontSize: 12.5, lineHeight: 1.5, color: COLORS.textMuted }}>{family.detail}</div>
                </div>
              </div>
            ))}
            {collection.length === 0 && <div style={{ fontSize: 13, color: COLORS.textMuted }}>No aircraft recorded yet.</div>}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(290px, 1fr))', gap: 20 }}>
            <div style={CARD}>
              <div style={CARD_HEAD}>Airlines flown</div>
              <div style={{ padding: '17px 20px', display: 'flex', gap: 7, flexWrap: 'wrap' }}>
                {profile.airlines.map((airline) => (
                  <Tag key={airline.id} label={airline.name} count={airline.flightCount} />
                ))}
                {profile.airlines.length === 0 && <span style={{ fontSize: 13, color: COLORS.textMuted }}>None yet.</span>}
              </div>
            </div>
            <div style={CARD}>
              <div style={CARD_HEAD}>Airports used</div>
              <div style={{ padding: '17px 20px', display: 'flex', gap: 7, flexWrap: 'wrap' }}>
                {[...profile.airports]
                  .sort((a, b) => b.timesUsed - a.timesUsed)
                  .slice(0, 12)
                  .map((airport) => (
                    <Tag key={airport.id} label={airport.iata ?? airport.name} count={airport.timesUsed} />
                  ))}
                {profile.airports.length > 12 && (
                  <span style={{ display: 'inline-flex', alignItems: 'center', padding: '6px 11px', fontSize: 12.5, color: COLORS.textDim }}>
                    and {profile.airports.length - 12} more
                  </span>
                )}
                {profile.airports.length === 0 && <span style={{ fontSize: 13, color: COLORS.textMuted }}>None yet.</span>}
              </div>
            </div>
          </div>
        </div>
      )}

      {section === 'Records' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16 }}>
            {records.map((record) => (
              <div
                key={record.label}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 9,
                  padding: '20px 22px',
                  background: COLORS.surfaceOverlay,
                  border: '1px solid ' + COLORS.line,
                  borderRadius: 13,
                }}
              >
                <span style={EYEBROW}>{record.label}</span>
                <span style={{ fontSize: 20, fontWeight: 600, letterSpacing: '-0.01em', lineHeight: 1.2, color: COLORS.text }}>
                  {record.value}
                </span>
                <span style={{ fontSize: 12.5, color: COLORS.textMuted, lineHeight: 1.5 }}>{record.note}</span>
              </div>
            ))}
            {records.length === 0 && <div style={{ fontSize: 13, color: COLORS.textMuted }}>Records fill in once you have flights.</div>}
          </div>

          <div style={CARD}>
            <div
              style={{
                display: 'flex',
                alignItems: 'baseline',
                justifyContent: 'space-between',
                gap: 12,
                padding: '16px 20px',
                borderBottom: '1px solid ' + COLORS.line,
                flexWrap: 'wrap',
              }}
            >
              <span style={{ fontSize: 16, fontWeight: 600 }}>Against your friends</span>
              <span style={{ fontSize: 12, color: COLORS.textDim }}>Only flights they share with you are counted</span>
            </div>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'minmax(0,1.1fr) repeat(3, minmax(0,1fr))',
                gap: 14,
                alignItems: 'center',
                padding: '11px 20px',
                background: '#082834',
                borderBottom: '1px solid ' + COLORS.line,
                fontSize: 11,
                letterSpacing: '0.12em',
                textTransform: 'uppercase',
                color: COLORS.textDim,
              }}
            >
              <span>Person</span>
              <span style={{ textAlign: 'right' }}>Distance</span>
              <span style={{ textAlign: 'right' }}>Flights</span>
              <span style={{ textAlign: 'right' }}>Countries</span>
            </div>
            {board.map((row) => (
              <div
                key={row.id}
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'minmax(0,1.1fr) repeat(3, minmax(0,1fr))',
                  gap: 14,
                  alignItems: 'center',
                  padding: '13px 20px',
                  borderBottom: '1px solid ' + COLORS.lineSoft,
                  background: row.me ? 'rgba(46,29,12,0.35)' : undefined,
                }}
              >
                <span style={{ display: 'flex', alignItems: 'center', gap: 11, minWidth: 0 }}>
                  <span
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: 30,
                      height: 30,
                      borderRadius: '50%',
                      background: COLORS.raised,
                      border: '1px solid ' + COLORS.lineStrong,
                      fontSize: 11,
                      fontWeight: 600,
                      color: COLORS.textMuted,
                      flexShrink: 0,
                    }}
                  >
                    {initialsOf(row.me ? displayName : row.name)}
                  </span>
                  <span style={{ fontSize: 13.5, fontWeight: row.me ? 700 : 500, color: row.me ? '#FFB067' : COLORS.text }}>{row.name}</span>
                </span>
                <span style={{ fontSize: 13.5, textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: row.me ? '#FFB067' : COLORS.text }}>
                  {formatKm(row.km)}
                </span>
                <span style={{ fontSize: 13.5, textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: COLORS.textMuted }}>
                  {row.flights}
                </span>
                <span style={{ fontSize: 13.5, textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: COLORS.textMuted }}>
                  {row.countries}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </PageShell>
  );
}

const dateStyle: CSSProperties = {
  height: 30,
  padding: '0 8px',
  borderRadius: 6,
  background: 'transparent',
  border: '1px solid ' + COLORS.lineStrong,
  fontSize: 12.5,
  color: COLORS.text,
  outline: 'none',
  colorScheme: 'dark',
  fontFamily: FONT_STACK,
};

function SplitRow({ label, count, pct, colour }: { label: string; count: number; pct: number; colour: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
      <span style={{ fontSize: 13, color: COLORS.bodyOnCard, flex: '0 0 76px' }}>{label}</span>
      <span style={{ flex: '1 1 auto', height: 7, borderRadius: 4, background: '#0D2C38', minWidth: 30, overflow: 'hidden' }}>
        <span style={{ display: 'block', height: '100%', width: pct + '%', background: colour, borderRadius: 4 }} />
      </span>
      <span style={{ fontSize: 12.5, color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums', flexShrink: 0 }}>{count}</span>
    </div>
  );
}

function Leaderboard({ title, rows }: { title: string; rows: { name: string; value: string }[] }) {
  return (
    <div style={CARD}>
      <div style={CARD_HEAD}>{title}</div>
      <div style={{ padding: '8px 0' }}>
        {rows.length === 0 && <div style={{ padding: '12px 20px', fontSize: 13, color: COLORS.textMuted }}>Nothing yet.</div>}
        {rows.map((row) => (
          <div key={row.name} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '9px 20px' }}>
            <span style={{ fontSize: 13.5, color: COLORS.text, flex: '1 1 auto', minWidth: 0 }}>{row.name}</span>
            <span style={{ fontSize: 12.5, color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums', flexShrink: 0 }}>{row.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Tag({ label, count }: { label: string; count: number }) {
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 7,
        padding: '6px 11px',
        borderRadius: 999,
        background: COLORS.raised,
        border: '1px solid ' + COLORS.lineStrong,
        fontSize: 12.5,
        letterSpacing: '0.04em',
        color: COLORS.bodyOnCard,
        whiteSpace: 'nowrap',
      }}
    >
      {label}
      <span style={{ color: COLORS.textDim, fontVariantNumeric: 'tabular-nums' }}>{count}</span>
    </span>
  );
}
