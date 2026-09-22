import { COLORS } from '../design/tokens';

/** The ring spinner used in buttons, lookups and the arrival screen. */
export function Spinner({ size = 14, color = COLORS.cyan }: { size?: number; color?: string }) {
  return (
    <span
      style={{
        width: size,
        height: size,
        border: `2px solid ${color}`,
        borderTopColor: 'transparent',
        borderRadius: '50%',
        animation: 'bbSpin 700ms linear infinite',
        display: 'inline-block',
        flexShrink: 0,
      }}
    />
  );
}
