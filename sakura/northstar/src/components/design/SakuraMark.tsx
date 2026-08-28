import { buildPetalMarks, dash } from "@/lib/design/petals";
import type { Style } from "@/lib/design/styleTypes";

type Props = {
  size: number;
  scale: number;
  thickness?: number;
  dotBase?: number;
  opacity?: number;
  /** Color for petal i (0-4), in header-gradient order: blue/red/pink/yellow/green */
  colorForPetal: (i: number) => string;
  spin?: boolean;
  /** Hero-flower-only: small radial ticks under each petal + a centre dot */
  stamens?: { color: (i: number) => string };
  centerDot?: { size: number; background: string; border: string };
  style?: Style;
};

/**
 * A five-petal sakura mark, each petal an outline traced with dashes + vertex dots (sketch style, not filled)
 * Used at every scale in the bundle: static header mark, spinning hero flower, crest, and the confirm/expired/success states
 */
export default function SakuraMark({
  size,
  scale,
  thickness = 3,
  dotBase = 8,
  opacity = 0.75,
  colorForPetal,
  spin = false,
  stamens,
  centerDot,
  style,
}: Props) {
  const marks = [];
  for (let i = 0; i < 5; i++) {
    marks.push(...buildPetalMarks(i * 72, colorForPetal(i), scale, thickness, dotBase, opacity));
  }
  if (stamens) {
    for (let i = 0; i < 5; i++) {
      marks.push(dash(i * 72, 14, 42, 3, stamens.color(i), 0.35));
    }
  }

  return (
    <div
      style={{
        position: "relative",
        width: size,
        height: size,
        animation: spin ? "kansei-spin 100s linear infinite" : undefined,
        ...style,
      }}
    >
      {marks.map((m, i) => (
        <div style={m.wrapStyle} key={i}>
          <div style={m.style} />
        </div>
      ))}
      {centerDot && (
        <div
          style={{
            position: "absolute",
            top: "50%",
            left: "50%",
            width: centerDot.size,
            height: centerDot.size,
            margin: `${-centerDot.size / 2}px 0 0 ${-centerDot.size / 2}px`,
            borderRadius: "50%",
            background: centerDot.background,
            border: centerDot.border,
          }}
        />
      )}
    </div>
  );
}
