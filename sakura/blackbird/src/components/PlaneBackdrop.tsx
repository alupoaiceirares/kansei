import { COLORS } from '../design/tokens';

const PLANE =
  'M120 40 L122.7 50.6 L140.3 64.7 L140.3 69.3 L122.7 63.1 L122.7 80.3 L129.4 86.5 L129.4 89.2 L121.4 84.9 L120.6 92 L119.4 92 L118.6 84.9 L110.6 89.2 L110.6 86.5 L117.3 80.3 L117.3 63.1 L99.7 69.3 L99.7 64.7 L117.3 50.6 Z';

const ARCS = [
  'M-120 700 C300 300 900 220 1560 420',
  'M-120 820 C260 500 1000 380 1560 200',
  'M-120 380 C400 120 980 140 1560 620',
  'M-120 170 C420 -50 1100 70 1560 480',
  'M-120 950 C380 730 1020 650 1560 770',
];

// path index, duration, negative begin offset so planes are mid-flight on load, scale
const FLIGHTS: [number, string, string, number][] = [
  [0, '150s', '-20s', 0.62],
  [1, '196s', '-118s', 0.5],
  [2, '168s', '-70s', 0.56],
  [2, '168s', '-144s', 0.44],
  [3, '212s', '-46s', 0.46],
  [4, '178s', '-134s', 0.54],
];

/** The fixed background every signed-in page sits on: five hairline flight paths with planes tracking them. */
export function PlaneBackdrop() {
  return (
    <svg
      style={{ position: 'fixed', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}
      viewBox="0 0 1440 900"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
    >
      <g fill="none" stroke={COLORS.texture} strokeWidth={1}>
        {ARCS.map((d) => (
          <path key={d} d={d} />
        ))}
      </g>
      <g fill={COLORS.plane} data-planes="true">
        {FLIGHTS.map(([arc, dur, begin, scale], i) => (
          <g key={i}>
            <animateMotion dur={dur} begin={begin} repeatCount="indefinite" rotate="auto" calcMode="linear" path={ARCS[arc]} />
            <g transform={`rotate(90) scale(${scale}) translate(-120 -66)`}>
              <path d={PLANE} />
            </g>
          </g>
        ))}
      </g>
    </svg>
  );
}
