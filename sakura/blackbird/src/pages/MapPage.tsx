import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { COLORS, FONT_STACK } from '../design/tokens';
import { PageShell } from '../components/PageShell';
import { Spinner } from '../components/Spinner';
import { PALETTES, WorldMap, type MapData, type MapMode, type PaletteName, type ProjectionName } from '../map/WorldMap';
import { fetchMapProfile, type MapProfile } from '../api/profile';
import { fetchMyFlights, fetchPreferences, savePreferences } from '../api/tailwind';
import type { UserFlight } from '../api/types';
import { formatNumber } from '../format';

const PREFS_KEY = 'wtw.map.prefs';

const PALETTE_LABELS: Record<PaletteName, string> = {
  Cyan: 'Cyan',
  Mixed: 'Mixed — ranked by frequency',
  Orange: 'Orange',
  Mono: 'Mono',
};

const CHIP: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 7,
  height: 32,
  padding: '0 12px',
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
const CHIP_LOCK: CSSProperties = { ...CHIP, borderColor: COLORS.lineSoft, color: COLORS.textFaint, cursor: 'not-allowed' };

const GROUP_LABEL: CSSProperties = { fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim };

const CARD: CSSProperties = {
  background: COLORS.surfaceOverlay,
  border: '1px solid ' + COLORS.line,
  borderRadius: 14,
  overflow: 'hidden',
};

type Prefs = {
  palette: PaletteName;
  mode: MapMode;
  projection: ProjectionName;
  routes: boolean;
  airports: boolean;
  transit: boolean;
};

const DEFAULTS: Prefs = {
  palette: 'Cyan',
  mode: 'Visited or not',
  projection: 'Natural Earth',
  routes: false,
  airports: true,
  transit: true,
};

/**
 * The account is the real home for these, so the map looks the same on another device. Browser storage
 * is kept as the immediate read, so the page does not flash the defaults while the account answers.
 */
function readLocalPrefs(): Prefs {
  try {
    const stored = JSON.parse(localStorage.getItem(PREFS_KEY) ?? '{}') as Partial<Prefs>;
    return { ...DEFAULTS, ...stored };
  } catch {
    return DEFAULTS;
  }
}

function writeLocalPrefs(prefs: Prefs): void {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
  } catch {
    // Blocked storage is fine, the account copy is the one that matters
  }
}

/** Only the keys this screen owns are read back, so another screen's preferences cannot bleed in. */
function prefsFrom(stored: Record<string, unknown>): Partial<Prefs> {
  const map = (stored[PREFS_KEY] ?? stored.map) as Partial<Prefs> | undefined;
  return map && typeof map === 'object' ? map : {};
}

function buildMapData(profile: MapProfile, flights: UserFlight[]): MapData {
  const visits: Record<string, number> = {};
  const names: Record<string, string> = {};
  for (const country of profile.countries.visited) {
    visits[country.code] = country.visitCount;
    names[country.code] = country.name;
  }
  for (const country of profile.countries.passedThrough) names[country.code] = country.name;

  return {
    visits,
    names,
    transitOnly: profile.countries.passedThrough.map((country) => country.code),
    airports: profile.airports.map((airport) => [airport.longitude, airport.latitude] as [number, number]),
    // Upcoming flights stay off the map until they have happened.
    routes: flights
      .filter((item) => !item.flight.upcoming)
      .map((item) => [
        [item.flight.departureAirport.longitude, item.flight.departureAirport.latitude],
        [item.flight.arrivalAirport.longitude, item.flight.arrivalAirport.latitude],
      ]) as [[number, number], [number, number]][],
  };
}

export function MapPage() {
  const [profile, setProfile] = useState<MapProfile | null>(null);
  const [flights, setFlights] = useState<UserFlight[]>([]);
  const [loading, setLoading] = useState(true);
  const [prefs, setPrefs] = useState<Prefs>(readLocalPrefs);
  const [storedPrefs, setStoredPrefs] = useState<Record<string, unknown>>({});

  useEffect(() => {
    let live = true;
    Promise.all([fetchMapProfile(), fetchMyFlights().catch(() => [])])
      .then(([loadedProfile, loadedFlights]) => {
        if (!live) return;
        setProfile(loadedProfile);
        setFlights(loadedFlights);
      })
      .catch(() => undefined)
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, []);

  useEffect(() => {
    let live = true;
    fetchPreferences()
      .then((loaded) => {
        if (!live) return;
        setStoredPrefs(loaded.preferences);
        setPrefs((current) => ({ ...current, ...prefsFrom(loaded.preferences) }));
      })
      .catch(() => undefined);
    return () => {
      live = false;
    };
  }, []);

  const update = (next: Partial<Prefs>) => {
    setPrefs((current) => {
      const merged = { ...current, ...next };
      writeLocalPrefs(merged);
      // The account copy is what another device reads, a failed save just leaves the local one
      savePreferences({ ...storedPrefs, [PREFS_KEY]: merged }).catch(() => undefined);
      return merged;
    });
  };

  const data = useMemo(() => (profile ? buildMapData(profile, flights) : null), [profile, flights]);

  if (loading || !profile || !data) {
    return (
      <PageShell maxWidth={1340}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading the map
        </div>
      </PageShell>
    );
  }

  const pal = PALETTES[prefs.palette];
  const ranked = !!pal.ranked;
  const frequency = prefs.mode === 'Frequency';
  const topCountries = [...profile.countries.visited].sort((a, b) => b.visitCount - a.visitCount).slice(0, 6);
  const max = topCountries[0]?.visitCount ?? 1;

  const legend = ranked
    ? [
        { color: '#1B6D84', label: '1–4', ring: null as string | null },
        { color: '#1B6D84', label: '5–9', ring: COLORS.cyanBright },
        { color: '#FF7A18', label: '10+', ring: null },
      ]
    : frequency
      ? [
          { color: pal.l1, label: '1', ring: null },
          { color: pal.l2, label: '2–4', ring: null },
          { color: pal.l3, label: '5–9', ring: null },
          { color: pal.l4, label: '10+', ring: null },
        ]
      : [{ color: pal.solid, label: 'Visited', ring: null }];

  return (
    <PageShell maxWidth={1340}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxWidth: 640 }}>
          <h1 style={{ margin: 0, fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em' }}>Map</h1>
          <p style={{ margin: 0, fontSize: 14, lineHeight: 1.6, color: COLORS.textMuted, textWrap: 'pretty' }}>
            Every country you have landed in. Switch to frequency shading to see how often, and turn on routes once you want the
            lines as well — with a full log they get busy, so airports alone is often the clearer read. Your choices are
            remembered.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
          <HeadStat value={formatNumber(profile.countries.visited.length)} label="countries visited" color={COLORS.cyan} />
          <HeadStat value={formatNumber(profile.countries.passedThrough.length)} label="transit only" color="#E8DFA4" />
          <HeadStat value={formatNumber(profile.stats.airportCount)} label="airports" color={COLORS.text} />
        </div>
      </div>

      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 18,
          alignItems: 'flex-end',
          padding: '16px 18px',
          background: COLORS.surfaceOverlay,
          border: '1px solid ' + COLORS.line,
          borderRadius: 12,
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
          <span style={GROUP_LABEL}>Colour</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
            <select
              value={prefs.palette}
              onChange={(event) => update({ palette: event.target.value as PaletteName })}
              style={{
                height: 32,
                padding: '0 10px',
                borderRadius: 999,
                background: COLORS.raised,
                border: '1px solid #7FCFE8',
                fontFamily: FONT_STACK,
                fontSize: 12.5,
                fontWeight: 500,
                color: COLORS.text,
                outline: 'none',
                cursor: 'pointer',
              }}
            >
              {(Object.keys(PALETTES) as PaletteName[]).map((name) => (
                <option key={name} value={name} style={{ background: COLORS.raised, color: COLORS.text }}>
                  {PALETTE_LABELS[name]}
                </option>
              ))}
            </select>
            <span style={{ display: 'flex', gap: 2 }}>
              {[pal.l1, pal.l2, pal.l3, pal.l4].map((colour, index) => (
                <span key={index} style={{ width: 9, height: 16, borderRadius: 2, background: colour }} />
              ))}
            </span>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
          <span style={GROUP_LABEL}>Shading</span>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
            {(['Frequency', 'Visited or not'] as MapMode[]).map((mode) => (
              <button
                key={mode}
                type="button"
                onClick={() => !ranked && update({ mode })}
                style={ranked ? CHIP_LOCK : prefs.mode === mode ? CHIP_ON : CHIP}
              >
                {mode}
              </button>
            ))}
            {ranked && <span style={{ fontSize: 11.5, color: COLORS.textDim }}>Mixed always ranks by frequency</span>}
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
          <span style={GROUP_LABEL}>Projection</span>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {(['Natural Earth', 'Orthographic', 'Equal Earth'] as ProjectionName[]).map((name) => (
              <button key={name} type="button" onClick={() => update({ projection: name })} style={prefs.projection === name ? CHIP_ON : CHIP}>
                {name}
              </button>
            ))}
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 7, marginLeft: 'auto' }}>
          <span style={GROUP_LABEL}>Overlays</span>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <button type="button" onClick={() => update({ transit: !prefs.transit })} style={prefs.transit ? CHIP_ON : CHIP}>
              Transit countries
            </button>
            <button type="button" onClick={() => update({ airports: !prefs.airports })} style={prefs.airports ? CHIP_ON : CHIP}>
              Airports
            </button>
            <button type="button" onClick={() => update({ routes: !prefs.routes })} style={prefs.routes ? CHIP_ON : CHIP}>
              Routes flown
            </button>
          </div>
        </div>
      </div>

      <div
        style={{
          position: 'relative',
          height: 560,
          background: '#071D28',
          border: '1px solid ' + COLORS.line,
          borderRadius: 14,
          overflow: 'hidden',
        }}
      >
        <WorldMap
          data={data}
          palette={prefs.palette}
          mode={prefs.mode}
          projection={prefs.projection}
          showRoutes={prefs.routes}
          showAirports={prefs.airports}
          showTransit={prefs.transit}
        />
      </div>

      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 18,
          alignItems: 'center',
          padding: '15px 18px',
          background: COLORS.surfaceOverlay,
          border: '1px solid ' + COLORS.line,
          borderRadius: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, flexWrap: 'wrap' }}>
          <span style={{ fontSize: 11.5, color: COLORS.textDim }}>{ranked || frequency ? 'Times visited' : 'Been there'}</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
            {legend.map((item, index) => (
              <span key={index} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ width: 15, height: 15, borderRadius: 3, border: '1px solid ' + (item.ring ?? '#1C4C5C'), background: item.color }} />
                <span style={{ fontSize: 11.5, color: COLORS.bodyOnCard }}>{item.label}</span>
              </span>
            ))}
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap', marginLeft: 'auto' }}>
          <LegendKey colour="#8C7F3A" label="Transit only" />
          <LegendKey colour="#0D2C38" label="Not visited" />
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 10, height: 10, borderRadius: '50%', background: COLORS.orange }} />
            <span style={{ fontSize: 11.5, color: COLORS.bodyOnCard }}>Airport</span>
          </span>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 18 }}>
        <div style={CARD}>
          <div style={{ padding: '15px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 }}>Most visited</div>
          <div style={{ padding: '8px 0' }}>
            {topCountries.length === 0 && (
              <div style={{ padding: '12px 20px', fontSize: 13, color: COLORS.textMuted }}>Nothing on the map yet.</div>
            )}
            {topCountries.map((country) => (
              <div key={country.code} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '9px 20px' }}>
                <span style={{ fontSize: 13.5, color: COLORS.text, minWidth: 0, flex: '0 0 132px' }}>{country.name}</span>
                <span style={{ flex: '1 1 auto', height: 7, borderRadius: 4, background: '#0D2C38', overflow: 'hidden', minWidth: 40 }}>
                  <span
                    style={{
                      display: 'block',
                      height: '100%',
                      width: Math.round((country.visitCount / max) * 100) + '%',
                      background: country.visitCount >= 10 ? pal.l4 : country.visitCount >= 5 ? pal.l3 : pal.l2,
                      borderRadius: 4,
                    }}
                  />
                </span>
                <span style={{ fontSize: 12.5, color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums', flexShrink: 0 }}>
                  {country.visitCount}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div style={CARD}>
          <div style={{ padding: '15px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 }}>Transit only</div>
          <div style={{ padding: '17px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap' }}>
              {profile.countries.passedThrough.length === 0 && (
                <span style={{ fontSize: 13, color: COLORS.textMuted }}>No transit-only countries yet.</span>
              )}
              {profile.countries.passedThrough.map((country) => (
                <span
                  key={country.code}
                  style={{
                    padding: '5px 11px',
                    borderRadius: 999,
                    background: '#2E2A14',
                    border: '1px solid #6E6431',
                    color: '#E8DFA4',
                    fontSize: 12,
                    fontWeight: 500,
                  }}
                >
                  {country.name}
                </span>
              ))}
            </div>
            <div style={{ fontSize: 12.5, lineHeight: 1.6, color: COLORS.textMuted }}>
              You changed planes in these without leaving the airport, so they are counted apart from the{' '}
              {profile.countries.visited.length} you actually visited. Turn the overlay off to hide them entirely.
            </div>
          </div>
        </div>
      </div>
    </PageShell>
  );
}

function HeadStat({ value, label, color }: { value: string; label: string; color: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <span style={{ fontSize: 22, fontWeight: 600, fontVariantNumeric: 'tabular-nums', lineHeight: 1, color }}>{value}</span>
      <span style={{ fontSize: 11.5, color: COLORS.textDim }}>{label}</span>
    </div>
  );
}

function LegendKey({ colour, label }: { colour: string; label: string }) {
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span style={{ width: 15, height: 15, borderRadius: 3, border: '1px solid #1C4C5C', background: colour }} />
      <span style={{ fontSize: 11.5, color: COLORS.bodyOnCard }}>{label}</span>
    </span>
  );
}
