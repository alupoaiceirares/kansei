type Props = {
  width?: number;
  height?: number;
};

/** WTW's eye mark, as used on its own site: cyan eye and Ws, orange plane in the iris, no wordmark. */
export default function WtwMark({ width = 132, height = 73 }: Props) {
  return (
    <svg viewBox="0 0 240 132" width={width} height={height} role="img" aria-label="WTW">
      <path d="M6 66 C50 20 190 20 234 66 C190 112 50 112 6 66 Z" fill="none" stroke="#2BB3D9" strokeWidth={4.2} />
      <path
        d="M7 66 L46 66 L56 79 L66 55 L76 79 L86 66 L104 66"
        fill="none"
        stroke="#2BB3D9"
        strokeWidth={5.2}
        strokeLinejoin="miter"
        strokeMiterlimit={12}
      />
      <path
        d="M136 66 L154 66 L164 79 L174 55 L184 79 L194 66 L233 66"
        fill="none"
        stroke="#2BB3D9"
        strokeWidth={5.2}
        strokeLinejoin="miter"
        strokeMiterlimit={12}
      />
      <path
        d="M99 66 A21 21 0 1 0 141 66 A21 21 0 1 0 99 66 Z M120 49.3 L121.7 55.8 L132.6 64.6 L132.6 67.4 L121.7 63.6 L121.7 74.2 L125.8 78.1 L125.8 79.8 L120.9 77.1 L120.4 81.5 L119.6 81.5 L119.1 77.1 L114.2 79.8 L114.2 78.1 L118.3 74.2 L118.3 63.6 L107.4 67.4 L107.4 64.6 L118.3 55.8 Z"
        fill="#FF7A18"
        fillRule="evenodd"
        transform="translate(120 66) scale(0.85) translate(-120 -66)"
      />
    </svg>
  );
}
