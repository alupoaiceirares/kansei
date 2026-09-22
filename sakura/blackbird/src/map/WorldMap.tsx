import { useEffect, useRef, useState } from 'react';
import { geoEqualEarth, geoNaturalEarth1, geoOrthographic, geoPath, type GeoProjection } from 'd3-geo';
import { select } from 'd3-selection';
import { zoom as d3zoom, zoomIdentity, type ZoomBehavior } from 'd3-zoom';
import 'd3-transition';
import { COLORS, FONT_STACK } from '../design/tokens';
import { countryIdOf, loadCountries, type CountryFeature } from './atlas';
import { COUNTRY_ID_BY_ALPHA2 } from './countryIds';

export type PaletteName = 'Cyan' | 'Mixed' | 'Orange' | 'Mono';
export type MapMode = 'Visited or not' | 'Frequency';
export type ProjectionName = 'Natural Earth' | 'Orthographic' | 'Equal Earth';

export const PALETTES: Record<PaletteName, { l1: string; l2: string; l3: string; l4: string; solid: string; ranked?: boolean }> = {
  Cyan: { l1: '#123E4D', l2: '#1B6D84', l3: '#2BB3D9', l4: '#7FE3FF', solid: '#2BB3D9' },
  Mixed: { l1: '#1B6D84', l2: '#1B6D84', l3: '#2BB3D9', l4: '#FF7A18', solid: '#2BB3D9', ranked: true },
  Orange: { l1: '#3A2410', l2: '#8A4A12', l3: '#FF7A18', l4: '#FFB067', solid: '#E0721A' },
  Mono: { l1: '#14414F', l2: '#2A6376', l3: '#4E8C9E', l4: '#8FB8C6', solid: '#4E8C9E' },
};

const BASE = '#0D2C38';
const BORDER = '#1C4C5C';
const SEA = '#071D28';
const TRANSIT = '#8C7F3A';

const W = 1200;
const H = 620;

export type MapData = {
  /** Visits per alpha-2 country code. */
  visits: Record<string, number>;
  /** Alpha-2 codes only passed through in transit. */
  transitOnly: string[];
  /** Airport coordinates, drawn as plain orange dots. */
  airports: [number, number][];
  /** Great-circle route pairs, one per flown leg. */
  routes: [[number, number], [number, number]][];
  /** Country name per alpha-2, for the tooltip. */
  names: Record<string, string>;
};

type Props = {
  data: MapData;
  palette: PaletteName;
  mode: MapMode;
  projection: ProjectionName;
  showRoutes: boolean;
  showAirports: boolean;
  showTransit: boolean;
};

function projectionFor(name: ProjectionName, features: CountryFeature[]): GeoProjection {
  const collection = { type: 'FeatureCollection', features } as never;
  if (name === 'Equal Earth') return geoEqualEarth().fitSize([W, H], collection);
  if (name === 'Orthographic') return geoOrthographic().rotate([-20, -25]).fitSize([W, H], collection);
  return geoNaturalEarth1().fitSize([W, H], collection);
}

/**
 * The full world map: countries shaded by the log, optional airports and routes, zoom 1x to 12x
 * with stroke widths divided by the zoom factor so borders stay hairline.
 */
export function WorldMap({ data, palette, mode, projection, showRoutes, showAirports, showTransit }: Props) {
  const hostRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const zoomRef = useRef<ZoomBehavior<SVGSVGElement, unknown> | null>(null);
  const [features, setFeatures] = useState<CountryFeature[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [k, setK] = useState(1);
  const [transform, setTransform] = useState('');
  const [tip, setTip] = useState<{ x: number; y: number; name: string; note: string } | null>(null);

  useEffect(() => {
    let live = true;
    loadCountries()
      .then((loaded) => live && setFeatures(loaded))
      .catch(() => live && setFailed(true));
    return () => {
      live = false;
    };
  }, []);

  useEffect(() => {
    if (!features || !svgRef.current) return;
    const svg = select(svgRef.current);
    const behaviour = d3zoom<SVGSVGElement, unknown>()
      .scaleExtent([1, 12])
      .translateExtent([
        [0, 0],
        [W, H],
      ])
      .on('zoom', (event) => {
        setTransform(event.transform.toString());
        setK(event.transform.k);
      });
    zoomRef.current = behaviour;
    svg.call(behaviour);
    return () => {
      svg.on('.zoom', null);
    };
  }, [features]);

  const zoomBy = (factor: number) => {
    if (!svgRef.current || !zoomRef.current) return;
    select(svgRef.current).transition().duration(240).call(zoomRef.current.scaleBy, factor);
  };

  const fit = () => {
    if (!svgRef.current || !zoomRef.current) return;
    select(svgRef.current).transition().duration(320).call(zoomRef.current.transform, zoomIdentity);
  };

  if (failed) {
    return (
      <div style={{ ...messageStyle }}>Could not load the border data. Check the connection and reload.</div>
    );
  }
  if (!features) {
    return <div style={{ ...messageStyle }}>Loading country borders…</div>;
  }

  const pal = PALETTES[palette];
  const ranked = !!pal.ranked;
  const proj = projectionFor(projection, features);
  const path = geoPath(proj);

  const idToAlpha2: Record<string, string> = {};
  for (const [alpha2, id] of Object.entries(COUNTRY_ID_BY_ALPHA2)) idToAlpha2[id] = alpha2;

  const visitsOf = (feature: CountryFeature) => data.visits[idToAlpha2[countryIdOf(feature)] ?? ''] ?? 0;
  const isTransit = (feature: CountryFeature) => data.transitOnly.includes(idToAlpha2[countryIdOf(feature)] ?? '');

  const fillFor = (feature: CountryFeature) => {
    const n = visitsOf(feature);
    if (!n) return showTransit && isTransit(feature) ? TRANSIT : BASE;
    if (ranked) return n >= 10 ? '#FF7A18' : '#1B6D84';
    if (mode === 'Visited or not') return pal.solid;
    if (n >= 10) return pal.l4;
    if (n >= 5) return pal.l3;
    if (n >= 2) return pal.l2;
    return pal.l1;
  };

  // The ranked scheme outlines the middle tier instead of giving it a third fill.
  const edgeFor = (feature: CountryFeature) => {
    if (!ranked) return BORDER;
    const n = visitsOf(feature);
    return n >= 5 && n < 10 ? COLORS.cyanBright : BORDER;
  };

  const edgeWidth = (feature: CountryFeature) => {
    if (!ranked) return 0.5;
    const n = visitsOf(feature);
    return n >= 5 && n < 10 ? 1.3 : 0.5;
  };

  return (
    <div ref={hostRef} style={{ position: 'relative', width: '100%', height: '100%', background: SEA }}>
      <svg
        ref={svgRef}
        viewBox={`0 0 ${W} ${H}`}
        preserveAspectRatio="xMidYMid slice"
        style={{ display: 'block', width: '100%', height: '100%', cursor: 'grab', touchAction: 'none' }}
        onMouseLeave={() => setTip(null)}
      >
        <g transform={transform}>
          <g>
            {features.map((feature, index) => {
              const alpha2 = idToAlpha2[countryIdOf(feature)] ?? '';
              const n = visitsOf(feature);
              return (
                <path
                  key={index}
                  d={path(feature as never) ?? undefined}
                  fill={fillFor(feature)}
                  stroke={edgeFor(feature)}
                  strokeWidth={edgeWidth(feature) / k}
                  style={{ cursor: n ? 'pointer' : 'default' }}
                  onMouseMove={(event) => {
                    const box = hostRef.current?.getBoundingClientRect();
                    if (!box) return;
                    setTip({
                      x: event.clientX - box.left,
                      y: event.clientY - box.top,
                      name: data.names[alpha2] ?? feature.properties.name,
                      note: n
                        ? n === 1
                          ? 'visited once'
                          : 'visited ' + n + ' times'
                        : isTransit(feature)
                          ? 'passed through in transit'
                          : 'not visited yet',
                    });
                  }}
                />
              );
            })}
          </g>

          <g fill="none">
            {showRoutes &&
              data.routes.map((route, index) => (
                <path
                  key={'r' + index}
                  d={path({ type: 'LineString', coordinates: route } as never) ?? undefined}
                  stroke={pal.l4}
                  strokeOpacity={0.7}
                  strokeWidth={1.6 / k}
                  strokeLinecap="round"
                />
              ))}
            {showAirports &&
              data.airports.map((point, index) => {
                const projected = proj(point);
                if (!projected) return null;
                return <circle key={'a' + index} cx={projected[0]} cy={projected[1]} r={3 / k} fill={COLORS.orange} />;
              })}
          </g>
        </g>
      </svg>

      <div style={{ position: 'absolute', top: 12, right: 12, display: 'flex', flexDirection: 'column', gap: 6, zIndex: 3 }}>
        <ZoomButton label="+" onClick={() => zoomBy(1.6)} />
        <ZoomButton label="−" onClick={() => zoomBy(1 / 1.6)} />
        <ZoomButton label="Fit" onClick={fit} small />
      </div>

      {tip && (
        <div
          style={{
            position: 'absolute',
            left: Math.min(tip.x + 14, (hostRef.current?.clientWidth ?? W) - 190),
            top: Math.max(tip.y - 14, 8),
            pointerEvents: 'none',
            zIndex: 4,
            padding: '8px 11px',
            borderRadius: 8,
            background: 'rgba(6,33,43,0.96)',
            border: '1px solid ' + COLORS.lineStrong,
            boxShadow: '0 10px 26px rgba(0,0,0,0.5)',
            fontFamily: FONT_STACK,
            color: COLORS.text,
            fontSize: 12.5,
            whiteSpace: 'nowrap',
          }}
        >
          <b style={{ fontWeight: 600 }}>{tip.name}</b>
          <span style={{ display: 'block', marginTop: 2, color: COLORS.textMuted, fontSize: 11.5 }}>{tip.note}</span>
        </div>
      )}
    </div>
  );
}

const messageStyle = {
  position: 'absolute' as const,
  inset: 0,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  fontFamily: FONT_STACK,
  color: COLORS.textDim,
  fontSize: 13,
  textAlign: 'center' as const,
  padding: 24,
  background: SEA,
};

function ZoomButton({ label, onClick, small = false }: { label: string; onClick: () => void; small?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        width: 34,
        height: 34,
        fontFamily: FONT_STACK,
        fontSize: small ? 10.5 : 15,
        letterSpacing: small ? '0.04em' : undefined,
        lineHeight: 1,
        color: COLORS.bodyOnCard,
        background: 'rgba(10,44,57,0.92)',
        border: '1px solid ' + COLORS.lineStrong,
        borderRadius: 8,
        cursor: 'pointer',
      }}
    >
      {label}
    </button>
  );
}
