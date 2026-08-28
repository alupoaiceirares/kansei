import { buildHexLattice } from "@/lib/design/hexLattice";

type Props = {
  vw: number;
  height: number;
  skip?: (cx: number, cy: number, R: number) => boolean;
};

/** Honeycomb of hair-thin hexagon outlines tiled across the whole page */
export default function HexLatticeBackground({ vw, height, skip = () => false }: Props) {
  const hexes = buildHexLattice(vw, height, skip);

  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        top: 0,
        width: "100%",
        height,
        zIndex: 0,
        pointerEvents: "none",
        overflow: "hidden",
      }}
    >
      {hexes.map((hx) => (
        <div style={hx.groupStyle} key={hx.key}>
          {hx.segments.map((sg, i) => (
            <div style={sg} key={i} />
          ))}
          {hx.hasDot && hx.dotStyle && <div style={hx.dotStyle} />}
        </div>
      ))}
    </div>
  );
}
