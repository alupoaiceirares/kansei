import { useEffect, useRef, useState } from 'react';
import { COLORS } from '../design/tokens';

const EYE = 'M6 66 C50 20 190 20 234 66 C190 112 50 112 6 66 Z';
const W_LEFT = 'M7 66 L46 66 L56 79 L66 55 L76 79 L86 66 L104 66';
const W_RIGHT = 'M136 66 L154 66 L164 79 L174 55 L184 79 L194 66 L233 66';
const IRIS =
  'M99 66 A21 21 0 1 0 141 66 A21 21 0 1 0 99 66 Z M120 49.3 L121.7 55.8 L132.6 64.6 L132.6 67.4 L121.7 63.6 L121.7 74.2 L125.8 78.1 L125.8 79.8 L120.9 77.1 L120.4 81.5 L119.6 81.5 L119.1 77.1 L114.2 79.8 L114.2 78.1 L118.3 74.2 L118.3 63.6 L107.4 67.4 L107.4 64.6 L118.3 55.8 Z';

const PULSE_MS = 3400;

type Props = {
  width?: number;
  height?: number;
  /** animated plays the continuous ECG pulse, static is the resting mark. */
  animated?: boolean;
  eyeStroke?: string;
  irisColor?: string;
  title?: string;
};

/** The WTW eye mark: eye outline, a W tracing each side, an orange plane in the iris. No wordmark. */
export function WtwLogo({
  width = 138,
  height = 76,
  animated = false,
  eyeStroke = COLORS.cyan,
  irisColor = COLORS.orange,
  title,
}: Props) {
  return (
    <svg
      viewBox="0 0 240 132"
      width={width}
      height={height}
      role={title ? 'img' : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
      style={{ display: 'block' }}
    >
      <path d={EYE} fill="none" stroke={eyeStroke} strokeWidth={4.2} />
      {animated ? (
        <>
          <path d={W_LEFT} fill="none" stroke={COLORS.lineStrong} strokeWidth={5.2} strokeLinejoin="miter" strokeMiterlimit={12} />
          <path d={W_RIGHT} fill="none" stroke={COLORS.lineStrong} strokeWidth={5.2} strokeLinejoin="miter" strokeMiterlimit={12} />
          <path
            d={W_LEFT}
            fill="none"
            stroke={COLORS.cyan}
            strokeWidth={5.2}
            strokeLinejoin="miter"
            strokeMiterlimit={12}
            strokeDasharray="142 142"
          >
            <animate
              attributeName="stroke-dashoffset"
              dur="3.4s"
              repeatCount="indefinite"
              calcMode="linear"
              values="142;0;0;0"
              keyTimes="0;0.3;0.9;1"
            />
            <animate attributeName="opacity" dur="3.4s" repeatCount="indefinite" values="1;1;0;0" keyTimes="0;0.9;0.97;1" />
          </path>
          <path
            d={W_RIGHT}
            fill="none"
            stroke={COLORS.cyan}
            strokeWidth={5.2}
            strokeLinejoin="miter"
            strokeMiterlimit={12}
            strokeDasharray="142 142"
          >
            <animate
              attributeName="stroke-dashoffset"
              dur="3.4s"
              repeatCount="indefinite"
              calcMode="linear"
              values="142;142;0;0"
              keyTimes="0;0.38;0.68;1"
            />
            <animate attributeName="opacity" dur="3.4s" repeatCount="indefinite" values="1;1;0;0" keyTimes="0;0.9;0.97;1" />
          </path>
          <circle r={2.6} fill="#CFF4FF" opacity={0}>
            <animateMotion dur="3.4s" repeatCount="indefinite" calcMode="linear" keyPoints="0;1;1" keyTimes="0;0.3;1" path={W_LEFT} />
            <animate attributeName="opacity" dur="3.4s" repeatCount="indefinite" values="0;1;1;0;0" keyTimes="0;0.02;0.3;0.33;1" />
          </circle>
          <circle r={2.6} fill="#CFF4FF" opacity={0}>
            <animateMotion
              dur="3.4s"
              repeatCount="indefinite"
              calcMode="linear"
              keyPoints="0;0;1;1"
              keyTimes="0;0.38;0.68;1"
              path={W_RIGHT}
            />
            <animate
              attributeName="opacity"
              dur="3.4s"
              repeatCount="indefinite"
              values="0;0;1;1;0;0"
              keyTimes="0;0.38;0.4;0.68;0.71;1"
            />
          </circle>
          <g style={{ animation: 'wtwBeat 3.4s ease-out infinite', transformBox: 'fill-box', transformOrigin: 'center' }}>
            <path
              d={IRIS}
              fill={irisColor}
              fillRule="evenodd"
              transform="translate(120 66) scale(0.85) translate(-120 -66)"
            >
              <animate
                attributeName="fill"
                dur="3.4s"
                repeatCount="indefinite"
                values="#FF7A18;#FF7A18;#FFC486;#FF7A18;#FF7A18"
                keyTimes="0;0.29;0.34;0.46;1"
              />
            </path>
          </g>
        </>
      ) : (
        <>
          <path d={W_LEFT} fill="none" stroke={COLORS.cyan} strokeWidth={5.2} strokeLinejoin="miter" strokeMiterlimit={12} />
          <path d={W_RIGHT} fill="none" stroke={COLORS.cyan} strokeWidth={5.2} strokeLinejoin="miter" strokeMiterlimit={12} />
          <path d={IRIS} fill={irisColor} fillRule="evenodd" transform="translate(120 66) scale(0.85) translate(-120 -66)" />
        </>
      )}
    </svg>
  );
}

/**
 * Static at rest, plays the pulse on hover. Leaving mid-cycle lets the current pulse finish
 * instead of cutting it off, same as the design.
 */
export function WtwLogoHover({ width, height }: { width?: number; height?: number }) {
  const [playing, setPlaying] = useState(false);
  const timer = useRef<number | null>(null);
  const startedAt = useRef(0);

  useEffect(() => () => {
    if (timer.current) window.clearTimeout(timer.current);
  }, []);

  const enter = () => {
    if (timer.current) {
      window.clearTimeout(timer.current);
      timer.current = null;
    }
    if (!playing) {
      startedAt.current = Date.now();
      setPlaying(true);
    }
  };

  const leave = () => {
    if (!playing || timer.current) return;
    const elapsed = (Date.now() - startedAt.current) % PULSE_MS;
    timer.current = window.setTimeout(() => {
      timer.current = null;
      setPlaying(false);
    }, PULSE_MS - elapsed);
  };

  return (
    <span
      onMouseEnter={enter}
      onMouseLeave={leave}
      style={{ display: 'flex', alignItems: 'center', gap: 11, cursor: 'pointer', flexShrink: 0 }}
    >
      <WtwLogo width={width} height={height} animated={playing} />
    </span>
  );
}
