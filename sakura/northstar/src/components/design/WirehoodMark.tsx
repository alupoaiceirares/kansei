"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type Props = {
  /** Overall size multiplier — the mark's native box is 316x90px at scale 1. */
  scale?: number;
};

/**
 * Wirehood's animated wordmark: a green cable arc draws in under/through the two
 * "o"s on mount, and replays on hover. Self-measures glyph positions via
 * getBoundingClientRect so the SVG overlay stays aligned to the live-rendered
 * "wirehood" text regardless of font-loading timing — ported from the same
 * component used in soundwave (sakura/soundwave's WirehoodMarkComponent).
 */
export default function WirehoodMark({ scale = 0.62 }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const wordRef = useRef<HTMLSpanElement>(null);
  const ooRef = useRef<HTMLSpanElement>(null);
  const markRef = useRef<HTMLSpanElement>(null);

  const [animKey, setAnimKey] = useState(0);
  const [geo, setGeo] = useState({
    leftRingX: 189,
    rightRingX: 215,
    ringCy: 36,
    letterTop: 25,
    letterBottom: 47,
    wordLeft: 121,
  });

  const measure = useCallback(() => {
    const wrapEl = wrapRef.current;
    const ooEl = ooRef.current;
    const markEl = markRef.current;
    const wordEl = wordRef.current;
    if (!wrapEl || !ooEl || !markEl) return;

    const wrapRect = wrapEl.getBoundingClientRect();
    const ooRect = ooEl.getBoundingClientRect();
    if (!wrapRect.width || !ooRect.height) return;

    const ooLeft = (ooRect.left - wrapRect.left) / scale;
    const cs = getComputedStyle(ooEl);
    const ls = parseFloat(cs.letterSpacing) || 0;
    const oAdv = (ooRect.width / scale - 2 * ls) / 2;

    const mkRect = markEl.getBoundingClientRect();
    const baseline = (mkRect.bottom - wrapRect.top) / scale;
    const xHeight = mkRect.height / scale;
    const overshoot = parseFloat(cs.fontSize) * 0.015;
    const glyphTop = baseline - xHeight - overshoot;
    const glyphBottom = baseline + overshoot;

    const wordLeft = wordEl ? (wordEl.getBoundingClientRect().left - wrapRect.left) / scale : 121;

    setGeo({
      wordLeft,
      leftRingX: ooLeft + oAdv / 2,
      rightRingX: ooLeft + oAdv * 1.5 + ls,
      ringCy: (glyphTop + glyphBottom) / 2,
      letterTop: glyphTop,
      letterBottom: glyphBottom,
    });
  }, [scale]);

  useEffect(() => {
    const raf1 = requestAnimationFrame(() => {
      measure();
      requestAnimationFrame(measure);
    });
    window.addEventListener("resize", measure);
    document.fonts?.ready?.then(measure);
    return () => {
      cancelAnimationFrame(raf1);
      window.removeEventListener("resize", measure);
    };
  }, [measure]);

  const { leftRingX: lx, rightRingX: rx, ringCy, letterTop, letterBottom, wordLeft } = geo;
  const bandRxE = (rx - lx) / 2;
  const bandRyE = bandRxE * 0.9;
  const bandD = `M${lx} ${letterTop} A ${bandRxE} ${bandRyE} 0 0 1 ${rx} ${letterTop}`;
  const midY = letterBottom + 15.5;
  const startX = wordLeft + 2.6;
  const jackX = 253;
  const cableLeftD = `M${startX} ${letterBottom} C ${startX} ${letterBottom + 9}, ${startX + 3.9} ${midY}, ${startX + 11.6} ${midY} L${lx - 10} ${midY} C ${lx - 5} ${midY}, ${lx} ${letterBottom + 9}, ${lx} ${letterBottom}`;
  const cableRightD = `M${rx} ${letterBottom} C ${rx} ${letterBottom + 9}, ${rx + 5} ${midY}, ${rx + 10} ${midY} L${jackX} ${midY}`;
  const jackCollarY = +(midY - 4.5).toFixed(1);
  const jackShaftY = +(midY - 2.6).toFixed(2);
  const jackTipY = +(midY - 2.3).toFixed(1);

  return (
    <div
      style={{ position: "relative", width: 316 * scale, height: 90 * scale, flexShrink: 0 }}
      onMouseEnter={() => setAnimKey((k) => k + 1)}
    >
      <div style={{ position: "absolute", top: 0, left: 0, width: 316, height: 90, transform: `scale(${scale})`, transformOrigin: "top left" }}>
        <div ref={wrapRef} style={{ position: "absolute", inset: 0 }}>
          <div
            style={{
              position: "absolute",
              top: 25,
              left: 0,
              width: 316,
              textAlign: "center",
              display: "flex",
              alignItems: "baseline",
              justifyContent: "center",
              font: "600 40px/1 'Archivo', sans-serif",
              fontStretch: "112%",
              letterSpacing: "0.65px",
              color: "#F2F0EC",
            }}
          >
            <span ref={wordRef}>wireh</span>
            <span ref={ooRef} style={{ display: "inline-block" }}>
              oo
            </span>
            <span>d</span>
            <span ref={markRef} style={{ display: "inline-block", width: 0, height: "1ex", verticalAlign: "baseline" }} />
          </div>
          <svg
            key={animKey}
            width={316}
            height={90}
            viewBox="0 0 316 90"
            fill="none"
            style={{ position: "absolute", left: 0, top: 0, overflow: "visible", pointerEvents: "none" }}
          >
            <path
              d={bandD}
              pathLength={1}
              stroke="#12B76B"
              strokeWidth={4.5}
              strokeLinecap="round"
              fill="none"
              strokeDasharray={1}
              style={{ animation: "whm-band 1.3s ease-out 1 forwards" }}
            />
            <path
              d={cableLeftD}
              pathLength={1}
              stroke="#12B76B"
              strokeWidth={4.5}
              strokeLinecap="round"
              fill="none"
              strokeDasharray={1}
              style={{ animation: "whm-cable 1.3s ease-out 1 forwards" }}
            />
            <path
              d={cableRightD}
              pathLength={1}
              stroke="#12B76B"
              strokeWidth={4.5}
              strokeLinecap="round"
              fill="none"
              strokeDasharray={1}
              style={{ animation: "whm-cable 1.3s ease-out 1 forwards" }}
            />
            <circle cx={lx} cy={ringCy} r={2.9} fill="#12B76B" style={{ animation: "whm-dot 1.3s ease-out 1 forwards" }} />
            <circle cx={rx} cy={ringCy} r={2.9} fill="#E23A3A" style={{ animation: "whm-dot 1.3s ease-out 1 forwards" }} />
            <g style={{ animation: "whm-jack 1.3s ease-out 1 forwards" }}>
              <rect x={jackX} y={jackCollarY} width={4.5} height={9} rx={1.6} fill="#E23A3A" />
              <rect x={jackX + 4.5} y={jackShaftY} width={5.2} height={5.2} rx={1.3} fill="#E23A3A" />
              <rect x={jackX + 10.3} y={jackShaftY} width={3.2} height={5.2} rx={1.3} fill="#E23A3A" />
              <rect x={jackX + 14.8} y={jackTipY} width={4.5} height={4.5} rx={2.3} fill="#E23A3A" />
            </g>
          </svg>
        </div>
      </div>
    </div>
  );
}
