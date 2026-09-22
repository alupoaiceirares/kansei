import { useEffect, useRef, useState } from 'react';
import { geoNaturalEarth1, geoPath } from 'd3-geo';
import { COLORS, FONT_STACK } from '../design/tokens';
import { countryIdOf, loadCountries, type CountryFeature } from './atlas';
import { COUNTRY_ID_BY_ALPHA2 } from './countryIds';

export type RouteStop = {
  code: string;
  lon: number;
  lat: number;
  /** Alpha-2 of the airport's country, used to colour that country in. */
  countryCode?: string;
  kind?: 'end' | 'transit' | 'soon';
  /** An unflown leg into this stop, drawn dashed and dimmed. */
  gap?: boolean;
};

type Props = {
  stops: RouteStop[];
  /** Draws every arc dashed, for an upcoming or unflown route. */
  dash?: boolean;
  accent?: string;
  /** Drops the airport labels, for card-sized thumbnails. */
  compact?: boolean;
};

const SEA = '#071D28';
const OTHER = '#0B2530';
const OTHER_LINE = '#123E4D';
const VISIT = '#1B6D84';
const VISIT_LINE = '#2BB3D9';
const TRANSIT = '#5C4718';
const TRANSIT_LINE = '#8A6A24';

const W = 1000;
const H = 420;

/**
 * Only the countries a route touches, drawn from real geometry and fitted to them, with
 * great-circle arcs between the stops. One component serves a full-width map and a card thumbnail:
 * label size, dot radius and stroke width are measured against the rendered box.
 */
export function RouteMap({ stops, dash = false, accent = COLORS.cyan, compact = false }: Props) {
  const hostRef = useRef<HTMLDivElement>(null);
  const [features, setFeatures] = useState<CountryFeature[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [box, setBox] = useState({ width: 0, height: 0 });

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
    const host = hostRef.current;
    if (!host) return;
    const measure = () => setBox({ width: host.clientWidth, height: host.clientHeight });
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(host);
    return () => observer.disconnect();
  }, []);

  const message = failed ? 'Map unavailable.' : features ? null : 'Loading map…';

  return (
    <div ref={hostRef} style={{ position: 'relative', width: '100%', height: '100%', background: SEA }}>
      {message && (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontFamily: FONT_STACK,
            color: COLORS.textFaint,
            fontSize: 12.5,
            textAlign: 'center',
            padding: 20,
          }}
        >
          {message}
        </div>
      )}
      {features && <RouteSvg features={features} stops={stops} dash={dash} accent={accent} compact={compact} box={box} />}
    </div>
  );
}

function RouteSvg({
  features,
  stops,
  dash,
  accent,
  compact,
  box,
}: Props & { features: CountryFeature[]; box: { width: number; height: number } }) {
  const kind: Record<string, 'visit' | 'transit'> = {};
  for (const stop of stops) {
    const id = stop.countryCode ? COUNTRY_ID_BY_ALPHA2[stop.countryCode.toUpperCase()] : undefined;
    if (id) kind[id] = stop.kind === 'transit' ? 'transit' : 'visit';
  }

  const involved = features.filter((item) => kind[countryIdOf(item)]);
  // Fit to the countries involved, else to the bare stop coordinates, else to the whole world so an
  // empty log still shows a map with nothing highlighted.
  const fitTarget = involved.length
    ? { type: 'FeatureCollection', features: involved }
    : stops.length > 0
      ? { type: 'MultiPoint', coordinates: stops.map((stop) => [stop.lon, stop.lat]) }
      : { type: 'Sphere' };

  const projection = geoNaturalEarth1().fitExtent(
    [
      [54, 48],
      [W - 54, H - 48],
    ],
    fitTarget as never,
  );
  const path = geoPath(projection);

  // The viewBox is fixed, so a small host shrinks everything with it: size marks off the real box.
  const k = box.width && box.height ? Math.min(box.width / W, box.height / H) : 1;
  const strokeScale = box.width && box.height ? Math.max(Math.min(box.width / W, box.height / H), 0.28) : 1;
  const markScale = Math.max(k, 0.28);
  const labelSize = 13 / Math.max(k, 0.2);
  const hideLabels = compact || k < 0.45;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid meet" style={{ display: 'block', width: '100%', height: '100%' }}>
      <g>
        {features
          .filter((item) => !kind[countryIdOf(item)])
          .map((item, index) => (
            <path key={'o' + index} d={path(item as never) ?? undefined} fill={OTHER} stroke={OTHER_LINE} strokeWidth={0.6} />
          ))}
      </g>
      <g>
        {involved.map((item, index) => {
          const transit = kind[countryIdOf(item)] === 'transit';
          return (
            <path
              key={'i' + index}
              d={path(item as never) ?? undefined}
              fill={transit ? TRANSIT : VISIT}
              stroke={transit ? TRANSIT_LINE : VISIT_LINE}
              strokeWidth={1.1}
            />
          );
        })}
      </g>
      <g fill="none">
        {stops.slice(0, -1).map((from, index) => {
          const to = stops[index + 1];
          const legDash = dash || from.kind === 'soon' || to.kind === 'soon' || to.gap;
          return (
            <path
              key={'a' + index}
              d={
                path({
                  type: 'LineString',
                  coordinates: [
                    [from.lon, from.lat],
                    [to.lon, to.lat],
                  ],
                } as never) ?? undefined
              }
              stroke={to.gap ? COLORS.textMuted : accent}
              strokeWidth={2.2 / strokeScale}
              strokeLinecap="round"
              strokeOpacity={to.gap ? 0.55 : 0.95}
              strokeDasharray={legDash ? 7 / strokeScale + ' ' + 7 / strokeScale : undefined}
            />
          );
        })}
      </g>
      <g>
        {stops.map((stop, index) => {
          const point = projection([stop.lon, stop.lat]);
          if (!point) return null;
          const [x, y] = point;
          const mid = stop.kind === 'transit';
          return (
            <g key={stop.code + index}>
              <circle
                cx={x}
                cy={y}
                r={(mid ? 3.4 : 5) / markScale}
                fill={mid ? COLORS.warning : accent}
                stroke={SEA}
                strokeWidth={1.4 / markScale}
              />
              {!hideLabels && (
                <text
                  x={x}
                  y={y - labelSize * 0.85}
                  textAnchor="middle"
                  fontFamily={FONT_STACK}
                  fontSize={labelSize}
                  fontWeight={600}
                  letterSpacing="0.06em"
                  fill={COLORS.text}
                  stroke="rgba(3,14,19,0.85)"
                  strokeWidth={labelSize * 0.26}
                  paintOrder="stroke"
                >
                  {stop.code}
                </text>
              )}
            </g>
          );
        })}
      </g>
    </svg>
  );
}
