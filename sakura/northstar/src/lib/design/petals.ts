import type { Style } from "./styleTypes";

export type Mark = { wrapStyle: Style; style: Style };

const IDENTITY_WRAP: Style = { position: "absolute", inset: 0 };

// One petal's silhouette, traced base -> tip(notch) -> base, in local coords (0,0) = flower center, +y local = outward
const PETAL_POINTS: [number, number][] = [
  [0, 0],
  [20, 36],
  [40, 76],
  [52, 120],
  [50, 156],
  [22, 175],
  [0, 165],
  [-22, 175],
  [-50, 156],
  [-52, 120],
  [-40, 76],
  [-20, 36],
  [0, 0],
];

const DEFAULT_SCALE = 2.2;

/**
 * Traces one petal outline as short dashes + vertex dots (sketch style, not filled), rotated to `angleDeg` around the flower center
 */
export function buildPetalMarks(
  angleDeg: number,
  color: string,
  scale: number = DEFAULT_SCALE,
  thickness = 3,
  dotBase = 8,
  opacity = 0.75,
): Mark[] {
  const rad = (angleDeg * Math.PI) / 180;
  const dir = [Math.sin(rad), -Math.cos(rad)];
  const perp = [Math.cos(rad), Math.sin(rad)];
  const pts = PETAL_POINTS.map(([lx, ly]) => {
    const x = lx * scale;
    const y = ly * scale;
    return [x * perp[0] + y * dir[0], x * perp[1] + y * dir[1]];
  });

  const marks: Mark[] = [];

  for (let i = 0; i < pts.length - 1; i++) {
    const [x1, y1] = pts[i];
    const [x2, y2] = pts[i + 1];
    const dx = x2 - x1;
    const dy = y2 - y1;
    const len = Math.hypot(dx, dy);
    const ang = (Math.atan2(dy, dx) * 180) / Math.PI;
    const mx = (x1 + x2) / 2;
    const my = (y1 + y2) / 2;
    const dashLen = len * 0.72;
    marks.push({
      wrapStyle: IDENTITY_WRAP,
      style: {
        position: "absolute",
        top: "50%",
        left: "50%",
        width: dashLen,
        height: thickness,
        marginLeft: mx - dashLen / 2,
        marginTop: my - thickness / 2,
        background: color,
        opacity,
        transform: `rotate(${ang.toFixed(1)}deg)`,
        borderRadius: 2,
      },
    });
  }

  for (let i = 0; i < pts.length - 1; i++) {
    const [x, y] = pts[i];
    const size = i % 3 === 0 ? dotBase : dotBase * 0.62;
    marks.push({
      wrapStyle: IDENTITY_WRAP,
      style: {
        position: "absolute",
        top: "50%",
        left: "50%",
        width: size,
        height: size,
        marginLeft: x - size / 2,
        marginTop: y - size / 2,
        borderRadius: "50%",
        background: color,
        opacity: Math.min(1, opacity + 0.05),
      },
    });
  }

  return marks;
}

/** A stamen tick: a short radial dash between rInner and rOuter, rotated to `angleDeg` */
export function dash(
  angleDeg: number,
  rInner: number,
  rOuter: number,
  width: number,
  color: string,
  opacity: number,
): Mark {
  const length = rOuter - rInner;
  return {
    wrapStyle: { position: "absolute", inset: 0, transform: `rotate(${angleDeg}deg)` },
    style: {
      position: "absolute",
      top: "50%",
      left: "50%",
      width,
      height: length,
      marginLeft: -width / 2,
      marginTop: -rOuter,
      borderRadius: width / 2,
      background: color,
      opacity,
    },
  };
}
