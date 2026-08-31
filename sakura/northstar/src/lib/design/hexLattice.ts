import type { Style } from "./styleTypes";
import { PALETTE } from "./palette";
import { r1 } from "./petals";

export type HexTile = {
  key: string;
  groupStyle: Style;
  segments: Style[];
  hasDot: boolean;
  dotStyle: Style | null;
};

export const HEX_R = 170;
const HIGHLIGHT_DURATION_S = 11;

/** Six vertices of a regular hexagon of radius R, flat-topped, in local coords. */
function hexVertices(R: number): [number, number][] {
  return Array.from({ length: 6 }, (_, k) => {
    const a = ((60 * k - 90) * Math.PI) / 180;
    return [R * Math.cos(a), R * Math.sin(a)];
  });
}

/**
 * One honeycomb row at radius R, centre line cy. `skipFn` receives the tile's centre (cx, cy, R) and returning true omits that tile, used to carve clearings around content (a card, the hero flower, the timeline column)
 */
export function buildHexRow(
  R: number,
  cy: number,
  rowIdx: number,
  skipFn: (cx: number, cy: number, R: number) => boolean,
  startN: number,
  vw: number,
): HexTile[] {
  const stepX = Math.sqrt(3) * R;
  const verts = hexVertices(R);
  const hexes: HexTile[] = [];
  const cols = Math.ceil(vw / stepX) + 2;
  let n = startN;

  for (let c = -1; c < cols; c++) {
    const cx = c * stepX + (rowIdx % 2 ? stepX / 2 : 0);
    if (skipFn(cx, cy, R)) continue;

    const full = PALETTE[n % PALETTE.length];
    const dim = `color-mix(in oklch, ${full}, white 74%)`;
    const phase = ((n % 5) / 5) * HIGHLIGHT_DURATION_S;

    const segments = verts.map((v, i) => {
      const w = verts[(i + 1) % 6];
      const dx = w[0] - v[0];
      const dy = w[1] - v[1];
      const len = Math.hypot(dx, dy);
      const ang = (Math.atan2(dy, dx) * 180) / Math.PI;
      const delay = phase + (i / 6) * HIGHLIGHT_DURATION_S;
      const segment: Style = {
        position: "absolute",
        left: r1(v[0]),
        top: r1(v[1]),
        width: r1(len),
        height: 1,
        background: dim,
        opacity: 0.75,
        transform: `rotate(${ang.toFixed(1)}deg)`,
        transformOrigin: "left center",
        "--full": full,
        "--dim": dim,
        animation: `kansei-highlight ${HIGHLIGHT_DURATION_S}s linear infinite`,
        animationDelay: `${delay.toFixed(2)}s`,
      };
      return segment;
    });

    const hasDot = (rowIdx + c) % 3 === 0;
    const path = "M" + verts.map((v) => `${v[0].toFixed(1)},${v[1].toFixed(1)}`).join(" L") + " Z";

    hexes.push({
      key: `${rowIdx}-${c}`,
      groupStyle: { position: "absolute", left: r1(cx), top: r1(cy), width: 0, height: 0 },
      segments,
      hasDot,
      dotStyle: hasDot
        ? ({
            position: "absolute",
            top: 0,
            left: 0,
            width: 6,
            height: 6,
            margin: "-3px 0 0 -3px",
            borderRadius: "50%",
            background: full,
            opacity: 0.85,
            boxShadow: `0 0 6px ${full}`,
            offsetPath: `path('${path}')`,
            offsetDistance: "0%",
            animation: `kansei-flow ${HIGHLIGHT_DURATION_S}s linear infinite`,
            animationDelay: `${phase.toFixed(2)}s`,
          } satisfies Style)
        : null,
    });

    n++;
  }

  return hexes;
}

/** Tiles the whole page height at fixed radius, honoring `skipFn` for clearings */
export function buildHexLattice(
  vw: number,
  height: number,
  skipFn: (cx: number, cy: number, R: number) => boolean,
  R: number = HEX_R,
): HexTile[] {
  const hexes: HexTile[] = [];
  const stepY = 1.5 * R;
  let rowIdx = 0;
  for (let cy = 0; cy <= height; cy += stepY, rowIdx++) {
    hexes.push(...buildHexRow(R, cy, rowIdx, skipFn, hexes.length, vw));
  }
  return hexes;
}
